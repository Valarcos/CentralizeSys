package com.centralizesys.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private Authentication authentication;

    private final String jwtSecret = "testSecretKeyMustBeLongEnoughToSatisfyReviewRequirementsOf512Bits";
    private final int jwtExpirationInMs = 604800000;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", jwtSecret);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationInMs", jwtExpirationInMs);
    }

    @Test
    void testGenerateToken() {
        when(authentication.getName()).thenReturn("testuser");

        String token = jwtTokenProvider.generateToken(authentication);

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void testGetUsernameFromJWT() {
        String token = Jwts.builder()
                .setSubject("testuser")
                .setIssuedAt(new Date())
                .setExpiration(new Date(new Date().getTime() + jwtExpirationInMs))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()), SignatureAlgorithm.HS512)
                .compact();

        // Note: In real impl we would use the generateToken result, but here we
        // construct valid token to test extraction
        String username = jwtTokenProvider.getUsernameFromJWT(token);
        assertEquals("testuser", username);
    }

    @Test
    void testValidateToken_Valid() {
        String token = Jwts.builder()
                .setSubject("testuser")
                .setIssuedAt(new Date())
                .setExpiration(new Date(new Date().getTime() + jwtExpirationInMs))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()), SignatureAlgorithm.HS512)
                .compact();

        assertTrue(jwtTokenProvider.validateToken(token));
    }

    @Test
    void testValidateToken_Expired() {
        String token = Jwts.builder()
                .setSubject("testuser")
                .setIssuedAt(new Date(new Date().getTime() - jwtExpirationInMs * 2))
                .setExpiration(new Date(new Date().getTime() - jwtExpirationInMs))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()), SignatureAlgorithm.HS512)
                .compact();

        assertFalse(jwtTokenProvider.validateToken(token));
    }

    @Test
    void testValidateToken_Malformed() {
        assertFalse(jwtTokenProvider.validateToken("malformed.token.here"));
    }
}
