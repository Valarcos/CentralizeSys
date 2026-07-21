package com.centralizesys.service;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.sales.VentaRequest;
import com.centralizesys.model.sales.VentaResponse;
import com.centralizesys.model.cheque.AlertaCheque;
import com.centralizesys.model.cheque.AlertaChequeRequest;
import com.centralizesys.repository.AlertaChequeRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChequeServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private VentaService ventaService;

    @Autowired
    private AlertaChequeRepository alertaChequeRepository;

    private Long testProductId;
    private Long testUserId;

    @BeforeEach
    void setupData() {
        this.testUserId = createTestUser();
        this.testProductId = createTestProduct("TEST-CHQ", 100.0, 100L);
    }

    @Test
    @DisplayName("IT-CHQ-01: Cheque Sale creates Venta and Alertas Cheques")
    void testRegistrarVentaConCheques() {
        // Arrange
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(testProductId);
        item.setCantidad(2L); // Total $200

        AlertaChequeRequest cheque1 = new AlertaChequeRequest();
        cheque1.setMonto(100.0);
        cheque1.setFechaCobro(LocalDate.now().plusDays(10));

        AlertaChequeRequest cheque2 = new AlertaChequeRequest();
        cheque2.setMonto(100.0);
        cheque2.setFechaCobro(LocalDate.now().plusDays(20));

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Cliente Cheque");
        request.setItems(List.of(item));
        request.setUsuarioId(testUserId);
        request.setCheques(List.of(cheque1, cheque2));

        // Note: No payments (pagos_venta) are sent at creation, they are pure debt/cheques.
        request.setPagos(List.of());

        // Act
        VentaResponse response = ventaService.registrarVentaConCheques(request);

        // Assert
        assertNotNull(response.getId());
        assertEquals("ACTIVA", response.getEstado()); // A cheque sale is a finalized commercial sale.

        // Verify Alertas Cheques in DB
        List<AlertaCheque> alertas = alertaChequeRepository.findByVentaId(response.getId());
        assertEquals(2, alertas.size());
        assertEquals("PENDIENTE", alertas.get(0).getEstado());
        assertEquals("PENDIENTE", alertas.get(1).getEstado());

        // Verify no payments were injected yet
        Integer countPagos = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pagos_venta WHERE venta_id = ?", Integer.class, response.getId());
        assertEquals(0, countPagos, "No payments should be recorded until physically collected");
    }

    @Test
    @DisplayName("IT-CHQ-02: Canceling a Cheque sale cascades to Alertas Cheques")
    void testAnularVentaCascadesToCheques() {
        // Arrange
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(testProductId);
        item.setCantidad(1L);

        AlertaChequeRequest cheque1 = new AlertaChequeRequest();
        cheque1.setMonto(100.0);
        cheque1.setFechaCobro(LocalDate.now().plusDays(5));

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Cancel Cheque");
        request.setItems(List.of(item));
        request.setUsuarioId(testUserId);
        request.setCheques(List.of(cheque1));

        VentaResponse response = ventaService.registrarVentaConCheques(request);
        Long ventaId = response.getId();

        // Act
        ventaService.anularVentaHistorica(ventaId);

        // Assert
        List<AlertaCheque> alertas = alertaChequeRepository.findByVentaId(ventaId);
        assertEquals(1, alertas.size());
        assertEquals("ANULADA", alertas.getFirst().getEstado());
    }
}
