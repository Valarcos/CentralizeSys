package com.centralizesys.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.time.Instant;


@Component
public class JwtTokenProvider {

    private final String jwtSecret;

    private final int jwtExpirationInMs;

    public JwtTokenProvider(@Value("${app.jwtSecret}") String jwtSecret,
                            @Value("${app.jwtExpirationInMs}") int jwtExpirationInMs) {
        if (jwtSecret == null || jwtSecret.isBlank() || "UNSET".equals(jwtSecret)) {
            throw new IllegalArgumentException("JWT_SECRET must be configured securely in the environment!");
        }
        if (jwtSecret.length() < 32) {
            throw new IllegalArgumentException("JWT_SECRET must be at least 32 characters long for HS512!");
        }
        this.jwtSecret = jwtSecret;
        this.jwtExpirationInMs = jwtExpirationInMs;
    }

    public String generateToken(Authentication authentication) {
        String username = authentication.getName();
        Instant nowInstant = Instant.now();
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(java.util.Date.from(nowInstant))
                .setExpiration(java.util.Date.from(nowInstant.plusMillis(jwtExpirationInMs)))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()), SignatureAlgorithm.HS512)
                .compact();
    }

    public String getUsernameFromJWT(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.getSubject();
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                    .build()
                    .parseClaimsJws(authToken);
            return true;
        } catch (SecurityException | MalformedJwtException | ExpiredJwtException | UnsupportedJwtException
                 | IllegalArgumentException e) {
            // Invalid JWT signature, Expired, Unsupported, or Empty
        }
        return false;
    }
}
