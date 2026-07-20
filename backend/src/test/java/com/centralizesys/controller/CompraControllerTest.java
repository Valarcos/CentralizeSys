package com.centralizesys.controller;

import com.centralizesys.model.purchase.CompraRequest;
import com.centralizesys.model.purchase.CompraResponse;
import com.centralizesys.security.CustomUserDetails;
import com.centralizesys.service.CompraService;
import com.centralizesys.service.AuditoriaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompraController.class)
@AutoConfigureMockMvc(addFilters = false)
@org.springframework.test.context.ActiveProfiles("test")
class CompraControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CompraService compraService;

    @MockBean
    private AuditoriaService auditoriaService;

    // Security Mocks
    @MockBean
    private com.centralizesys.security.JwtTokenProvider jwtTokenProvider;
    @MockBean
    private com.centralizesys.security.CustomUserDetailsService customUserDetailsService;
    @MockBean
    private com.centralizesys.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    @DisplayName("registrarCompra populates UsuarioId from SecurityContext")
    void registrarCompra_PopulatesUserId() throws Exception {
        Long userId = 77L;

        // Mock Security Context
        CustomUserDetails mockUser = mock(CustomUserDetails.class);
        when(mockUser.getId()).thenReturn(userId);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(mockUser);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        try {
            // Prepare Request (without user ID)
            CompraRequest request = new CompraRequest();
            request.setProveedor("Prov 1");
            request.setNroComprobante("A-001");
            // request.setUsuarioId(null); // Explicitly null/ignoring

            // Mock Service Response
            CompraResponse response = new CompraResponse(10L, java.time.LocalDateTime.of(2026, java.time.Month.JANUARY, 1, 12, 0), "Prov 1", "A-001", 100.0,
                    Collections.emptyList());
            when(compraService.registrarCompra(any(CompraRequest.class))).thenReturn(response);

            // Act
            mockMvc.perform(post("/api/compras")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            // Assert: Service called with request having userId = 77
            ArgumentCaptor<CompraRequest> captor = ArgumentCaptor.forClass(CompraRequest.class);
            verify(compraService).registrarCompra(captor.capture());

            assertEquals(userId, captor.getValue().getUsuarioId());

        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}

