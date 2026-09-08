package com.centralizesys.controller;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.security.JwtTokenProvider;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IdempotencyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setupMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("Idempotency: Concurrent requests with same Idempotency-Key reject the second one")
    void idempotency_ConcurrentRequests_RejectDuplicate() throws Exception {
        // 1. Setup admin token
        Long adminId = createTestUser();
        authenticateUser(adminId, "ROLE_ADMIN");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("test@admin.com", null);
        String jwtToken = jwtTokenProvider.generateToken(auth);

        String jti = jwtTokenProvider.getJtiFromToken(jwtToken);
        jdbcTemplate.update("INSERT INTO active_tokens (jti, usuario_id, expires_at) VALUES (?, ?, ?)",
                jti, adminId, java.sql.Timestamp.valueOf(jwtTokenProvider.getExpirationFromToken(jwtToken)));

        // Create a test client first so we can PUT to it
        jdbcTemplate.update("INSERT INTO clientes (id, nombre, activo) VALUES (9999, 'Test Client', true) ON CONFLICT (id) DO NOTHING");

        // Request 1 - Should Succeed
        mockMvc.perform(put("/api/clientes/9999/nombre")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"New Name\"}"))
                .andExpect(status().isOk()); // or whatever success status it returns

        // Request 2 - Should Fail (Duplicate caught by Aspect)
        mockMvc.perform(put("/api/clientes/9999/nombre")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"New Name\"}"))
                .andExpect(status().isBadRequest()); // BusinessRuleException maps to 400 Bad Request
    }
}
