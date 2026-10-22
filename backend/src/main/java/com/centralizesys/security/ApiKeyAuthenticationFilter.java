package com.centralizesys.security;

import com.centralizesys.exception.GlobalExceptionHandler;
import com.centralizesys.model.auth.ApiKey;
import com.centralizesys.service.ApiKeyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Security filter for machine-to-machine (M2M) external API integrations.
 *
 * <p>Validates incoming {@code X-API-KEY} headers against hashed storage, enforces
 * per-key rate limiting (default 10 rpm) for dynamic serverless consumers, protects against
 * unauthenticated brute-force attacks via IP throttling, and isolates privileges by
 * establishing a security context with {@code ROLE_EXTERNAL_SERVICE}.
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);
    private static final String EXTERNAL_API_PREFIX = "/api/external/";
    private static final String HEADER_API_KEY = "X-API-KEY";
    private static final int IP_BRUTE_FORCE_MAX_FAILURES = 20;

    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    /**
     * In-memory token bucket per API Key ID resetting every 60 seconds.
     * Prevents runaway serverless loops or scrapers from exceeding the allowed rate_limit_rpm.
     */
    private final Cache<Long, AtomicInteger> keyRateLimiter = Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .maximumSize(1_000)
            .build();

    /**
     * Brute-force IP throttle for unauthenticated requests (missing/invalid keys).
     * Blocks IP after 20 failed attempts in 60 seconds.
     */
    private final Cache<String, AtomicInteger> ipBruteForceLimiter = Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .maximumSize(5_000)
            .build();

    @org.springframework.beans.factory.annotation.Autowired
    public ApiKeyAuthenticationFilter(Optional<ApiKeyService> apiKeyServiceOptional, ObjectMapper objectMapper) {
        this.apiKeyService = apiKeyServiceOptional.orElse(null);
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService, ObjectMapper objectMapper) {
        this.apiKeyService = apiKeyService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!path.startsWith(EXTERNAL_API_PREFIX) || apiKeyService == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);

        // Check if caller IP is brute-force throttled
        AtomicInteger ipFailureCount = ipBruteForceLimiter.get(clientIp, ip -> new AtomicInteger(0));
        if (ipFailureCount != null && ipFailureCount.get() >= IP_BRUTE_FORCE_MAX_FAILURES) {
            log.warn("Blocked brute-force API key attempt from IP '{}' on path '{}'", clientIp, path);
            sendErrorResponse(response, HttpStatus.TOO_MANY_REQUESTS.value(),
                    "Demasiados intentos fallidos. Intente nuevamente en 1 minuto.", 60);
            return;
        }

        String rawApiKey = request.getHeader(HEADER_API_KEY);
        if (!StringUtils.hasText(rawApiKey)) {
            if (ipFailureCount != null) {
                ipFailureCount.incrementAndGet();
            }
            log.warn("Rejected unauthenticated external request from IP '{}' on path '{}': missing {}",
                    clientIp, path, HEADER_API_KEY);
            sendErrorResponse(response, HttpStatus.UNAUTHORIZED.value(),
                    "Cabecera X-API-KEY no provista o vacía.", null);
            return;
        }

        Optional<ApiKey> optApiKey = apiKeyService.validateKey(rawApiKey.trim());
        if (optApiKey.isEmpty()) {
            if (ipFailureCount != null) {
                ipFailureCount.incrementAndGet();
            }
            log.warn("Rejected unauthorized external request from IP '{}' on path '{}': invalid API key",
                    clientIp, path);
            sendErrorResponse(response, HttpStatus.UNAUTHORIZED.value(),
                    "API Key inválida o inactiva.", null);
            return;
        }

        ApiKey apiKey = optApiKey.get();

        // Enforce per-key rate limiting (default: 10 rpm)
        AtomicInteger requestCounter = keyRateLimiter.get(apiKey.getId(), id -> new AtomicInteger(0));
        int currentCount = requestCounter != null ? requestCounter.incrementAndGet() : 1;

        if (currentCount > apiKey.getRateLimitRpm()) {
            log.warn("Rate limit exceeded for API Key id={} name='{}' (limit={} rpm, count={})",
                    apiKey.getId(), apiKey.getNombre(), apiKey.getRateLimitRpm(), currentCount);
            sendErrorResponse(response, HttpStatus.TOO_MANY_REQUESTS.value(),
                    "Límite de peticiones excedido para esta API Key. Intente más tarde.", 60);
            return;
        }

        // Throttled access recording
        apiKeyService.recordAccessThrottled(apiKey.getId());

        // Establish Role-isolated SecurityContext
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                apiKey.getNombre(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_EXTERNAL_SERVICE"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private void sendErrorResponse(HttpServletResponse response, int status, String message, Integer retryAfter)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        if (retryAfter != null) {
            response.setHeader("Retry-After", String.valueOf(retryAfter));
        }

        GlobalExceptionHandler.ErrorResponse errorDetails =
                new GlobalExceptionHandler.ErrorResponse(status, message, System.currentTimeMillis());
        response.getWriter().write(objectMapper.writeValueAsString(errorDetails));
    }

    private String extractClientIp(HttpServletRequest request) {
        String cfIp = request.getHeader("CF-Connecting-IP");
        if (StringUtils.hasText(cfIp)) {
            return cfIp.trim();
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
