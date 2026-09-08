package com.centralizesys.controller;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.auth.AuthRequest;
import com.centralizesys.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setupMockMvc() {
        // Here we DO NOT disable filters, we apply springSecurity()
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("AuthController: permitAll() allows unauthenticated access to /login")
    void permitAll_AllowsLogin() throws Exception {
        AuthRequest request = new AuthRequest("invalid@email.com", "wrongpass");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // We expect 401 Unauthorized because credentials are bad,
                // NOT 403 Forbidden which would happen if permitAll() was broken
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AuthController: isAuthenticated() blocks unauthenticated access to /logout")
    void isAuthenticated_BlocksLogout() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON))
                // Must be blocked by Spring Security before reaching controller
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AuthController: isAuthenticated() allows authenticated access to /logout")
    void isAuthenticated_AllowsLogout() throws Exception {
        Long adminId = createTestUser();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("test@admin.com", null);
        String jwtToken = jwtTokenProvider.generateToken(auth);

        String jti = jwtTokenProvider.getJtiFromToken(jwtToken);
        jdbcTemplate.update("INSERT INTO active_tokens (jti, usuario_id, expires_at) VALUES (?, ?, ?)",
                jti, adminId, java.sql.Timestamp.valueOf(jwtTokenProvider.getExpirationFromToken(jwtToken)));

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON))
                // Returns 200 OK since token is valid and endpoint is accessible
                .andExpect(status().isOk());
    }
}
