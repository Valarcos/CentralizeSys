package com.centralizesys.controller;

import com.centralizesys.model.dto.PageResponse;
import com.centralizesys.model.gastos.GastoCaja;
import com.centralizesys.model.gastos.GastoCajaAnulacionRequest;
import com.centralizesys.model.gastos.GastoCajaRequest;
import com.centralizesys.service.GastoCajaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
class GastoCajaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GastoCajaService gastoCajaService;

    // -----------------------------------------------------------------------
    // GET /api/gastos
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("GET /api/gastos - returns PageResponse with content and pagination metadata")
    void getGastos_ReturnsPageResponse() throws Exception {
        GastoCaja gasto = new GastoCaja();
        gasto.setId(1L);
        gasto.setMonto(100.0);
        gasto.setMotivo("Test");

        PageResponse<GastoCaja> page = new PageResponse<>(List.of(gasto), 0L, 15L, 1L, 1L);

        // Default params: page=0, size=15, year=null, month=null, day=null
        when(gastoCajaService.obtenerGastos(eq(0L), eq(15L), isNull(), isNull(), isNull()))
                .thenReturn(page);

        mockMvc.perform(get("/api/gastos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].monto").value(100.0))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(15));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("GET /api/gastos - forwards year/month/day params to service")
    void getGastos_WithDateParams_ForwardsToService() throws Exception {
        PageResponse<GastoCaja> emptyPage = new PageResponse<>(List.of(), 0L, 15L, 0L, 0L);

        when(gastoCajaService.obtenerGastos(eq(0L), eq(15L), eq(2026), eq(10), isNull()))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/api/gastos")
                        .param("year", "2026")
                        .param("month", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // -----------------------------------------------------------------------
    // POST /api/gastos
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("POST /api/gastos - creates gasto and returns ID")
    void crearGasto_ReturnsId() throws Exception {
        GastoCajaRequest request = new GastoCajaRequest();
        request.setMonto(500.0);
        request.setMotivo("Retiro");
        request.setCategoria("Otros");

        when(gastoCajaService.crearGasto(any(GastoCajaRequest.class), anyLong())).thenReturn(7L);

        mockMvc.perform(post("/api/gastos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().string("7"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("POST /api/gastos - fails validation when monto is missing")
    void crearGasto_FailsValidation_NoMonto() throws Exception {
        GastoCajaRequest request = new GastoCajaRequest();
        request.setMotivo("Retiro");
        // No monto

        mockMvc.perform(post("/api/gastos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // POST /api/gastos/{id}/anular
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("POST /api/gastos/{id}/anular - voids gasto")
    void anularGasto_Success() throws Exception {
        GastoCajaAnulacionRequest request = new GastoCajaAnulacionRequest();
        request.setRazonAnulacion("Me equivoqué");

        mockMvc.perform(post("/api/gastos/1/anular")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("POST /api/gastos/{id}/anular - returns 404 when not found")
    void anularGasto_NotFound() throws Exception {
        doThrow(new com.centralizesys.exception.ResourceNotFoundException("Gasto Caja", 999L))
                .when(gastoCajaService).anularGasto(eq(999L), any(), anyLong());

        mockMvc.perform(post("/api/gastos/999/anular"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("POST /api/gastos/{id}/anular - returns 400 when business rule violated")
    void anularGasto_BusinessRuleViolation() throws Exception {
        doThrow(new com.centralizesys.exception.BusinessRuleException("El gasto ya está anulado."))
                .when(gastoCajaService).anularGasto(eq(1L), any(), anyLong());

        mockMvc.perform(post("/api/gastos/1/anular"))
                .andExpect(status().isBadRequest());
    }
}
