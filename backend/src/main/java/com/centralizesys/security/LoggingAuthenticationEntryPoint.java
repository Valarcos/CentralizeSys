package com.centralizesys.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Replaces the anonymous lambda in SecurityConfig to add structured logging for
 * every request that Spring Security rejects before reaching a controller.
 *
 * <p>This is the production-grade mechanism for catching ghost 401s — such as
 * URL double-prefix bugs or token validation failures — that would otherwise be
 * completely invisible in the application logs.
 *
 * <p>Each rejection produces a WARN-level entry in {@code app.log} containing:
 * <ul>
 *   <li>HTTP method and full request URI</li>
 *   <li>Caller IP address (resolved correctly via {@code SERVER_FORWARD_HEADERS_STRATEGY=native})</li>
 *   <li>Spring Security's reason string (e.g., "Full authentication is required")</li>
 * </ul>
 */
@Component
public class LoggingAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(LoggingAuthenticationEntryPoint.class);

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        String ip = request.getRemoteAddr();

        log.warn("Unauthorized request — method={}, uri={}, ip={}, reason={}",
                method, uri, ip, authException.getMessage());

        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
    }
}
