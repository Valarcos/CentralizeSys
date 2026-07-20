package com.centralizesys.controller;

import com.centralizesys.model.debt.PagoDeudaRequest;
import com.centralizesys.model.sales.VentaRequest;
import com.centralizesys.model.sales.VentaResponse;
import com.centralizesys.repository.PendingSaleRepository;
import com.centralizesys.repository.VentaRepository;
import com.centralizesys.service.PendingSaleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PendingSaleController.class)
@org.springframework.test.context.ActiveProfiles("test")
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
class PendingSaleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PendingSaleService pendingSaleService;

    @MockBean
    private PendingSaleRepository pendingSaleRepository;

    @MockBean
    private VentaRepository ventaRepository;

    @MockBean
    private com.centralizesys.service.AuditoriaService auditoriaService;

    @MockBean
    private com.centralizesys.security.JwtTokenProvider jwtTokenProvider;

    @MockBean
    private com.centralizesys.security.CustomUserDetailsService customUserDetailsService;

    @MockBean
    private com.centralizesys.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    // SecurityUtils.getAuthenticatedUserId() usually reads from SecurityContext.
    // However, SecurityUtils is a static method. In WebMvcTest, we can either mock static
    // or rely on the actual @WithMockUser to populate the context.
    // The codebase's SecurityUtils uses SecurityContextHolder.getContext().getAuthentication().getPrincipal().
    // If the principal is a string (e.g. from standard @WithMockUser), it might throw ClassCastException.
    // Assuming SecurityUtils has a fallback to 0L or we can test the controller's delegation.

    @Test
    @DisplayName("crearPendiente - delegates to service and returns 201")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void crearPendiente_Returns201() throws Exception {
        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Client");

        when(pendingSaleService.crearPendiente(any(VentaRequest.class), any())).thenReturn(1L);

        mockMvc.perform(post("/api/ventas-pendientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$").value(1));

        verify(pendingSaleService).crearPendiente(any(VentaRequest.class), any());
    }

    @Test
    @DisplayName("registrarPago - delegates to service and returns 201")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void registrarPago_Returns201() throws Exception {
        PagoDeudaRequest request = new PagoDeudaRequest();
        request.setMontoPago(100.0);

        mockMvc.perform(post("/api/ventas-pendientes/1/pagos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(request))))
                .andExpect(status().isCreated());

        verify(pendingSaleService).registrarPago(eq(1L), any(), any());
    }

    @Test
    @DisplayName("finalizarVenta - delegates to service and returns 200")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void finalizarVenta_Returns200() throws Exception {
        VentaResponse response = new VentaResponse(1L, null, "Client", "Vendor", 100.0, 0.0, "M", Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), "FINALIZADA", 50.0);
        when(pendingSaleService.finalizarVenta(eq(1L), any())).thenReturn(response);

        mockMvc.perform(post("/api/ventas-pendientes/1/finalizar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteNombre").value("Client"));

        verify(pendingSaleService).finalizarVenta(eq(1L), any());
    }

    @Test
    @DisplayName("cancelarPendiente - delegates to service and returns 200")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void cancelarPendiente_Returns200() throws Exception {
        mockMvc.perform(post("/api/ventas-pendientes/1/cancelar"))
                .andExpect(status().isOk());

        verify(pendingSaleService).cancelarPendiente(eq(1L), any());
    }

    @Test
    @DisplayName("modificarCarrito - delegates to service and returns 200")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void modificarCarrito_Returns200() throws Exception {
        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Client");

        VentaResponse response = new VentaResponse(1L, null, "Client", "Vendor", 100.0, 0.0, "M", Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), "PENDIENTE", 50.0);
        when(pendingSaleService.modificarCarrito(eq(1L), any(VentaRequest.class), any())).thenReturn(response);

        mockMvc.perform(put("/api/ventas-pendientes/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteNombre").value("Client"));

        verify(pendingSaleService).modificarCarrito(eq(1L), any(VentaRequest.class), any());
    }

    @Test
    @DisplayName("anularPago - delegates to service and returns 204")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void anularPago_Returns204() throws Exception {
        mockMvc.perform(delete("/api/ventas-pendientes/1/pagos/2"))
                .andExpect(status().isNoContent());

        verify(pendingSaleService).anularPago(eq(1L), eq(2L), any());
    }
}


