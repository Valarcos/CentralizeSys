package com.centralizesys.security;

import com.centralizesys.model.auth.ActiveToken;
import com.centralizesys.repository.ActiveTokenRepository;
import com.centralizesys.service.ActiveTokenCacheService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * JWT Authentication Filter.
 *
 * <p>Intercepts every incoming request to validate the JWT and establish the
 * security context. After structural JWT validation (signature + expiry), the filter
 * additionally verifies that the token's JTI is recognized as an active session — first
 * via the in-memory Caffeine cache (fast path), then via the database (fallback for
 * cold-cache scenarios after a server restart).
 *
 * <p><b>Session revocation:</b> If the JTI is not found in either the cache or the DB,
 * the request proceeds without an authenticated principal, resulting in a 401 Unauthorized.
 * This is the mechanism that invalidates old sessions when a user logs in on a new device.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final ActiveTokenCacheService activeTokenCacheService;
    private final ActiveTokenRepository activeTokenRepository;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                   CustomUserDetailsService customUserDetailsService,
                                   ActiveTokenCacheService activeTokenCacheService,
                                   ActiveTokenRepository activeTokenRepository) {
        this.tokenProvider = tokenProvider;
        this.customUserDetailsService = customUserDetailsService;
        this.activeTokenCacheService = activeTokenCacheService;
        this.activeTokenRepository = activeTokenRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String jti = tokenProvider.getJtiFromToken(jwt);

                // Session validation: cache first, then DB fallback.
                if (!isSessionActive(jti)) {
                    // JTI not recognized — session was revoked (e.g., logout or new login from another device).
                    log.debug("Rejected request: JTI '{}' is not an active session.", jti);
                    filterChain.doFilter(request, response);
                    return;
                }

                String username = tokenProvider.getUsernameFromJWT(jwt);
                UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                org.springframework.security.core.context.SecurityContextHolder
                        .getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            // Intentionally swallowed: invalid tokens result in an unauthenticated request (401),
            // not an unhandled exception. Detailed errors are logged at debug level.
            log.debug("JWT authentication failed for request to {}: {}", request.getRequestURI(), ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    // -------------------------------------------------------------------------
    // Private Helpers
    // -------------------------------------------------------------------------

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * Checks if the given JTI corresponds to a currently active session.
     *
     * <p>Fast path: checks the Caffeine cache (O(1)).
     * Fallback path: queries the DB if the cache misses (handles cold-cache scenarios after restart).
     * If found in DB but not in cache, the entry is re-inserted into the cache (self-healing).
     *
     * @param jti the JWT ID claim to validate.
     * @return {@code true} if the session is active; {@code false} if it has been revoked.
     */
    private boolean isSessionActive(String jti) {
        // Fast path: in-memory cache
        if (activeTokenCacheService.isValid(jti)) {
            return true;
        }

        // Fallback: database (handles cold-cache after restart or cache eviction)
        Optional<ActiveToken> dbToken = activeTokenRepository.findByJti(jti);
        if (dbToken.isPresent()) {
            // Self-healing: re-populate cache so subsequent requests use the fast path
            activeTokenCacheService.put(jti, dbToken.get().getUsuarioId());
            log.debug("Cache miss for JTI '{}' — restored from DB.", jti);
            return true;
        }

        return false;
    }
}
