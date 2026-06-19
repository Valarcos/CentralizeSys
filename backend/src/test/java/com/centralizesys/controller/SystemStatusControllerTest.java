package com.centralizesys.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemStatusController.class)
class SystemStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private com.centralizesys.service.AuditoriaService auditoriaService;

    @MockBean
    private com.centralizesys.security.JwtTokenProvider jwtTokenProvider;

    @MockBean
    private com.centralizesys.security.CustomUserDetailsService customUserDetailsService;

    @MockBean
    private com.centralizesys.service.ActiveTokenCacheService activeTokenCacheService;

    @MockBean
    private com.centralizesys.repository.ActiveTokenRepository activeTokenRepository;

    @Test
    @WithMockUser
    void getAlerts_shouldReturnEmptyList() throws Exception {
        mockMvc.perform(get("/api/system/alerts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts", hasSize(0)));
    }
}
