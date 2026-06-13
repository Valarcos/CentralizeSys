package com.centralizesys.security;

import com.centralizesys.repository.ActiveTokenRepository;
import com.centralizesys.service.ActiveTokenCacheService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    @Mock
    private ActiveTokenCacheService activeTokenCacheService;

    @Mock
    private ActiveTokenRepository activeTokenRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Valid token with active session sets authentication context")
    void testDoFilterInternal_ValidToken() throws ServletException, IOException {
        String token = "valid.jwt.token";
        String jti   = "test-jti-uuid";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getJtiFromToken(token)).thenReturn(jti);
        when(tokenProvider.getUsernameFromJWT(token)).thenReturn("testuser");

        // Session is active in cache (fast path)
        when(activeTokenCacheService.isValid(jti)).thenReturn(true);

        UserDetails userDetails = new User("testuser", "password", Collections.emptyList());
        when(customUserDetailsService.loadUserByUsername("testuser")).thenReturn(userDetails);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("testuser", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Valid token with revoked session (not in cache or DB) is rejected")
    void testDoFilterInternal_RevokedSession() throws ServletException, IOException {
        String token = "valid.but.revoked.token";
        String jti   = "revoked-jti-uuid";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getJtiFromToken(token)).thenReturn(jti);

        // Session not in cache, not in DB either
        when(activeTokenCacheService.isValid(jti)).thenReturn(false);
        when(activeTokenRepository.findByJti(jti)).thenReturn(Optional.empty());

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // No authentication set — request proceeds as anonymous (401 later)
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
        verify(customUserDetailsService, never()).loadUserByUsername(any());
    }

    @Test
    @DisplayName("Cache miss but DB hit: re-populates cache (self-healing)")
    void testDoFilterInternal_CacheMiss_DbHit_RepopulatesCache() throws ServletException, IOException {
        String token  = "cold.cache.token";
        String jti    = "cold-cache-jti";
        Long   userId = 42L;
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getJtiFromToken(token)).thenReturn(jti);
        when(tokenProvider.getUsernameFromJWT(token)).thenReturn("testuser");

        // Cache miss, but DB has it
        when(activeTokenCacheService.isValid(jti)).thenReturn(false);
        com.centralizesys.model.auth.ActiveToken dbToken = new com.centralizesys.model.auth.ActiveToken();
        dbToken.setJti(jti);
        dbToken.setUsuarioId(userId);
        when(activeTokenRepository.findByJti(jti)).thenReturn(Optional.of(dbToken));

        UserDetails userDetails = new User("testuser", "password", Collections.emptyList());
        when(customUserDetailsService.loadUserByUsername("testuser")).thenReturn(userDetails);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Authentication must be set
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        // Cache must have been repopulated
        verify(activeTokenCacheService).put(jti, userId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Invalid token does not set authentication context")
    void testDoFilterInternal_InvalidToken() throws ServletException, IOException {
        String token = "invalid.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenProvider.validateToken(token)).thenReturn(false);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }
}
