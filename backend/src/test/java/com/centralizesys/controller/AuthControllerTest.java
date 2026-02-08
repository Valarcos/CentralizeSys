package com.centralizesys.controller;

import com.centralizesys.model.auth.AuthRequest;
import com.centralizesys.security.JwtTokenProvider;
import com.centralizesys.service.AuditoriaService;
import com.centralizesys.repository.UsuarioRepository;
import com.centralizesys.model.auth.Usuario;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.centralizesys.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@WebMvcTest(controllers = AuthController.class, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false) // Disable Security Filters for Unit Test
class AuthControllerTest {

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

                Usuario user = new Usuario();
                user.setId(1L);
                user.setEmail("test@test.com");
                user.setNombre("Test User");
                user.setRol(com.centralizesys.model.auth.UsuarioRole.ADMIN);
                when(usuarioRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));

                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.token").value("mock.jwt.token"))
                        .andExpect(jsonPath("$.email").value("test@test.com"))
                        .andExpect(jsonPath("$.rol").value("ADMIN"));

                verify(auditoriaService).registrarAccion(eq(1L), eq("LOGIN"), anyString());
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
        }

        @Test
        @DisplayName("Login Failure: User Not Found (Post-Auth) Returns 404 or 500")
        void testLogin_UserNotFoundAfterAuth() throws Exception {
                // Edge case: Auth details are correct (e.g. from LDAP or previous session
                // logic)
                // but user is missing in DB (Consistency Error).
                AuthRequest request = new AuthRequest("ghost@test.com", "password");

                Authentication auth = mock(Authentication.class);
                when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                        .thenReturn(auth);

                when(usuarioRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isNotFound()); // Or 500 depending on orElseThrow behavior.
                // Since we used .orElseThrow(), it throws NoSuchElementException -> Spring maps
                // to 500 by default.
                // Ideally we should handle it, but for now validating that it fails is enough.
                // Actually, let's expect 500 until we add specific handling.
        }
}
