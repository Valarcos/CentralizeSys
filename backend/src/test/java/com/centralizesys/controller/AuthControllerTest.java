package com.centralizesys.controller;

import com.centralizesys.model.auth.AuthRequest;
import com.centralizesys.model.auth.Usuario;
import com.centralizesys.repository.ActiveTokenRepository;
import com.centralizesys.repository.UsuarioRepository;
import com.centralizesys.security.JwtAuthenticationFilter;
import com.centralizesys.security.JwtTokenProvider;
import com.centralizesys.service.ActiveTokenCacheService;
import com.centralizesys.service.AuditoriaService;
import com.centralizesys.service.LoginAttemptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false) // Disable Security Filters for Unit Test
class AuthControllerTest {

        private static final LocalDateTime MOCK_EXPIRES_AT =
                LocalDateTime.of(2026, Month.JANUARY, 22, 10, 0, 0);

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private AuthenticationManager authenticationManager;

        @MockBean
        private JwtTokenProvider tokenProvider;

        @MockBean
        private AuditoriaService auditoriaService;

        @MockBean
        private UsuarioRepository usuarioRepository;

        @MockBean
        private LoginAttemptService loginAttemptService;

        @MockBean
        private ActiveTokenRepository activeTokenRepository;

        @MockBean
        private ActiveTokenCacheService activeTokenCacheService;

        @Autowired
        private ObjectMapper objectMapper;

        @Test
        @DisplayName("Login Success: Returns 200 + Token with Role")
        void testLogin_Success() throws Exception {
                AuthRequest request = new AuthRequest("test@test.com", "password");

                Authentication auth = mock(Authentication.class);
                when(auth.getName()).thenReturn("testuser");

                when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                        .thenReturn(auth);

                when(tokenProvider.generateToken(auth)).thenReturn("mock.jwt.token");
                when(tokenProvider.getJtiFromToken("mock.jwt.token")).thenReturn("mock-jti-uuid");
                when(tokenProvider.getExpirationFromToken("mock.jwt.token")).thenReturn(MOCK_EXPIRES_AT);

                Usuario user = new Usuario();
                user.setId(1L);
                user.setEmail("test@test.com");
                user.setNombre("Test User");
                user.setRol(com.centralizesys.model.auth.UsuarioRole.ADMIN);
                when(usuarioRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));

                // loginAttemptService.checkAndThrowIfBlocked does nothing (no block)
                // loginAttemptService.resetOnSuccess does nothing (void)
                // activeTokenRepository/activeTokenCacheService calls are void — no stubbing needed

                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.token").value("mock.jwt.token"))
                        .andExpect(jsonPath("$.email").value("test@test.com"))
                        .andExpect(jsonPath("$.rol").value("ADMIN"));

                verify(auditoriaService).registrarAccion(eq(1L), eq("LOGIN"), anyString());
                verify(activeTokenRepository).deleteByUsuarioId(1L);
                verify(activeTokenRepository).save(1L, "mock-jti-uuid", MOCK_EXPIRES_AT);
                verify(activeTokenCacheService).put("mock-jti-uuid", 1L);
        }

        @Test
        @DisplayName("Login Bad Creds: Returns 401")
        void testLogin_BadCreds() throws Exception {
                AuthRequest request = new AuthRequest("test@test.com", "wrong");

                when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                        .thenThrow(new BadCredentialsException("Bad credentials"));

                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isUnauthorized());

                // Failed attempt must be recorded
                verify(loginAttemptService).recordFailedAttempt(eq("test@test.com"), any(), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("Login Failure: User Not Found (Post-Auth) Returns 404")
        void testLogin_UserNotFoundAfterAuth() throws Exception {
                AuthRequest request = new AuthRequest("ghost@test.com", "password");

                Authentication auth = mock(Authentication.class);
                when(auth.getName()).thenReturn("ghost@test.com");
                when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                        .thenReturn(auth);
                when(tokenProvider.generateToken(auth)).thenReturn("ghost.jwt.token");
                when(tokenProvider.getJtiFromToken("ghost.jwt.token")).thenReturn("ghost-jti");
                when(tokenProvider.getExpirationFromToken("ghost.jwt.token")).thenReturn(MOCK_EXPIRES_AT);

                when(usuarioRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isNotFound());
        }
}
