package com.centralizesys.service;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.debt.DeudaResponse;
import com.centralizesys.model.debt.PagoDeudaRequest; // NEW
import com.centralizesys.model.enums.DebtStatus;
import com.centralizesys.model.sales.Venta;
import com.centralizesys.repository.DeudoresRepository;
import com.centralizesys.repository.VentaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DeudoresServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DeudoresService deudoresService;

    @Autowired
    private DeudoresRepository deudoresRepository;

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private VentaService ventaService;

    @Autowired
    private com.centralizesys.repository.AlertaChequeRepository alertaChequeRepository;

    private Long createTestVenta(Long userId, String clientName, Double total) {
        Venta venta = new Venta();
        venta.setFecha(LocalDateTime.of(2026, java.time.Month.JANUARY, 1, 12, 0));
        venta.setClienteNombre(clientName);
        venta.setTotalVenta(total);
        venta.setUsuarioId(userId);
        venta.setTipoVenta("MINORISTA");
        venta.setEstado("ACTIVA");
        return ventaRepository.saveVenta(venta);
    }

    private Long createTestDeuda(Long ventaId, String clientName, Double amount) {
        deudoresRepository.save(ventaId, clientName, null, amount);
        return deudoresRepository.findAll().stream()
                .filter(d -> d.getVentaId().equals(ventaId))
                .findFirst()
                .orElseThrow().getId();
    }

    @Test
    @DisplayName("Should handle partial and full payments with correct Double precision")
    void shouldHandlePaymentsRefactor() {
        Long userId = createTestUser();
        Long ventaId = createTestVenta(userId, "Test Debtor", 100.0);
        Long deudaId = createTestDeuda(ventaId, "Test Debtor", 100.50);

        DeudaResponse initialDebt = deudoresService.getById(deudaId);
        assertEquals(DebtStatus.PENDIENTE.name(), initialDebt.getEstado());

        // 2. Partial Payment ($50.20)
        PagoDeudaRequest p1 = new PagoDeudaRequest();
        p1.setMontoPago(50.20);
        p1.setMetodoPagoId(1L);
        p1.setObservaciones("Test Partial");
        DeudaResponse partial = deudoresService.registrarPago(deudaId, java.util.List.of(p1), userId);

        assertEquals(50.30, partial.getMontoDeuda(), 0.001, "Balance should be 50.30");
        assertEquals(DebtStatus.PARCIAL.name(), partial.getEstado());

        // 3. Full Payment ($50.30)
        PagoDeudaRequest p2 = new PagoDeudaRequest();
        p2.setMontoPago(50.30);
        p2.setMetodoPagoId(1L);
        p2.setObservaciones("Test Full");
        DeudaResponse full = deudoresService.registrarPago(deudaId, java.util.List.of(p2), userId);

        assertEquals(0.00, full.getMontoDeuda(), 0.001);
        assertEquals(DebtStatus.PAGADO.name(), full.getEstado());
    }

    @Test
    @DisplayName("Should handle tiny rounding issues gracefully")
    void shouldHandleRounding() {
        // Scenario: 10.00 debt. Payment of 3.33 repeated 3 times.
        Long userId = createTestUser();
        Long ventaId = createTestVenta(userId, "Math User", 10.0);
        Long deudaId = createTestDeuda(ventaId, "Math User", 10.00);

        // Helper
        PagoDeudaRequest p = new PagoDeudaRequest();
        p.setMetodoPagoId(1L);
        p.setObservaciones("Round");

        // Pay 1: 3.33
        p.setMontoPago(3.33);
        DeudaResponse r1 = deudoresService.registrarPago(deudaId, java.util.List.of(p), userId);
        assertEquals(6.67, r1.getMontoDeuda());

        // Pay 2: 3.33
        // Reuse object? Better new one to avoid side effects if service modifies it (it
        // shouldn't)
        PagoDeudaRequest p2 = new PagoDeudaRequest();
        p2.setMetodoPagoId(1L);
        p2.setMontoPago(3.33);
        DeudaResponse r2 = deudoresService.registrarPago(deudaId, java.util.List.of(p2), userId);
        assertEquals(3.34, r2.getMontoDeuda());

        // Pay 3: 3.34 (Clean finish)
        PagoDeudaRequest p3 = new PagoDeudaRequest();
        p3.setMetodoPagoId(1L);
        p3.setMontoPago(3.34);
        DeudaResponse r3 = deudoresService.registrarPago(deudaId, java.util.List.of(p3), userId);
        assertEquals(0.00, r3.getMontoDeuda());
        assertEquals(DebtStatus.PAGADO.name(), r3.getEstado());
    }

    @Test
    @DisplayName("Should record payment in pagos_deuda history")
    void shouldRecordPaymentInHistory() {
        Long userId = createTestUser();
        Long ventaId = createTestVenta(userId, "History User", 100.0);
        Long deudaId = createTestDeuda(ventaId, "History User", 100.0);

        // Act
        PagoDeudaRequest p = new PagoDeudaRequest();
        p.setMontoPago(20.0);
        p.setMetodoPagoId(1L);
        p.setObservaciones("First Installment");
        deudoresService.registrarPago(deudaId, java.util.List.of(p), userId);

        // Assert History
        var pagos = deudoresService.getPagos(deudaId);
        assertEquals(1, pagos.size());
        assertEquals(20.0, pagos.getFirst().getMonto());
        assertEquals(1L, pagos.getFirst().getMetodoPagoId());
        assertEquals("First Installment", pagos.getFirst().getObservaciones());
        assertNotNull(pagos.getFirst().getUsuarioNombre());
        assertEquals("Efectivo", pagos.getFirst().getMetodoPagoNombre()); // Assuming 1=Efectivo
    }

    @Test
    @DisplayName("Should successfully retrieve an existing debt by Venta ID")
    void shouldGetDebtByVentaId_WhenExists() {
        Long userId = createTestUser();
        Long ventaId = createTestVenta(userId, "Fiado Client", 200.0);
        createTestDeuda(ventaId, "Fiado Client", 200.0);

        DeudaResponse debt = deudoresService.getByVentaId(ventaId);

        assertNotNull(debt);
        assertEquals(ventaId, debt.getVentaId());
        assertEquals("Fiado Client", debt.getClienteNombre());
        assertEquals(200.0, debt.getMontoOriginal());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when no debt exists for Venta ID")
    void shouldThrowResourceNotFound_WhenVentaIdDoesNotExist() {
        Long userId = createTestUser();
        Long ventaId = createTestVenta(userId, "Standard Client", 150.0); // NO debt created

        com.centralizesys.exception.ResourceNotFoundException exception = assertThrows(
                com.centralizesys.exception.ResourceNotFoundException.class,
                () -> deudoresService.getByVentaId(ventaId)
        );

        assertTrue(exception.getMessage().contains("Deuda de Venta with ID " + ventaId + " not found"));
    }

    @Test
    @DisplayName("ERP Standard: registrarPago with cheque immediately reduces debt and verifies full lifecycle (cobro, undo, annulment)")
    void registrarPago_WithCheque_InstantlyReducesDebtAndFullLifecycle() {
        Long userId = createTestUser();
        Long ventaId = createTestVenta(userId, "Cheque Client", 300.0);
        Long deudaId = createTestDeuda(ventaId, "Cheque Client", 300.0);

        PagoDeudaRequest chequePayment = new PagoDeudaRequest();
        chequePayment.setMontoPago(300.0);
        chequePayment.setMetodoPagoId(3L); // Cheque ID
        chequePayment.setFechaCobro(java.time.LocalDate.now().plusDays(15));
        chequePayment.setObservaciones("Cheque Payment");

        // 1. Initial entry: Debt must be reduced immediately under ERP standard
        DeudaResponse response = deudoresService.registrarPago(deudaId, java.util.List.of(chequePayment), userId);
        assertEquals(0.0, response.getMontoDeuda(), 0.001, "Debt must be immediately reduced to 0.0 upon receiving cheque");
        assertEquals(DebtStatus.PAGADO.name(), response.getEstado());

        // Verify AlertaCheque was created in DB
        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate = (org.springframework.jdbc.core.JdbcTemplate) org.springframework.test.util.ReflectionTestUtils.getField(deudoresRepository, "jdbcTemplate");
        assertNotNull(jdbcTemplate);
        Long chequeId = jdbcTemplate.queryForObject(
                "SELECT id FROM alertas_cheques WHERE venta_id = ? AND tipo_origen = 'DEUDA_FIADO' AND estado = 'PENDIENTE'",
                Long.class, ventaId);
        assertNotNull(chequeId);

        // 2. Cashing the cheque: debt remains 0.0, cheque becomes COBRADO, and pago_deuda is recorded
        ventaService.cobrarCheque(chequeId, 1L, userId);
        DeudaResponse postCobroDebt = deudoresService.getById(deudaId);
        assertEquals(0.0, postCobroDebt.getMontoDeuda(), 0.001, "Cashing cheque must NOT double-deduct debt");
        assertEquals(DebtStatus.PAGADO.name(), postCobroDebt.getEstado());

        Integer pagoDeudaCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pagos_deuda WHERE deuda_id = ? AND (anulado = false OR anulado IS NULL)",
                Integer.class, deudaId);
        assertEquals(1, pagoDeudaCount);

        // 3. Undoing cobro (↩️ Deshacer): cheque returns to PENDIENTE, payment voided, debt remains 0.0
        ventaService.cancelarCobroCheque(chequeId, userId);
        DeudaResponse postUndoDebt = deudoresService.getById(deudaId);
        assertEquals(0.0, postUndoDebt.getMontoDeuda(), 0.001, "Undoing cobro preserves debt balance since cheque is back in portfolio");
        String chequeEstadoAfterUndo = jdbcTemplate.queryForObject(
                "SELECT estado FROM alertas_cheques WHERE id = ?", String.class, chequeId);
        assertEquals("PENDIENTE", chequeEstadoAfterUndo);

        // 4. Annuling the cheque: since the cheque is cancelled permanently, debt MUST be restored to 300.0
        ventaService.anularCheque(chequeId, userId);
        DeudaResponse postAnnulDebt = deudoresService.getById(deudaId);
        assertEquals(300.0, postAnnulDebt.getMontoDeuda(), 0.001, "Annuling cheque must restore debt back to open account");
        assertEquals(DebtStatus.PENDIENTE.name(), postAnnulDebt.getEstado());

        String chequeEstadoAfterAnnul = jdbcTemplate.queryForObject(
                "SELECT estado FROM alertas_cheques WHERE id = ?", String.class, chequeId);
        assertEquals("ANULADA", chequeEstadoAfterAnnul);
    }

    @Test
    @DisplayName("ERP Standard: registrarPago with mixed cash and cheque reduces debt by total amount")
    void registrarPago_MixedCashAndCheque() {
        Long userId = createTestUser();
        Long ventaId = createTestVenta(userId, "Mixed Client", 500.0);
        Long deudaId = createTestDeuda(ventaId, "Mixed Client", 500.0);

        PagoDeudaRequest cash = new PagoDeudaRequest();
        cash.setMontoPago(200.0);
        cash.setMetodoPagoId(1L); // Efectivo

        PagoDeudaRequest cheque = new PagoDeudaRequest();
        cheque.setMontoPago(300.0);
        cheque.setMetodoPagoId(3L); // Cheque
        cheque.setFechaCobro(java.time.LocalDate.now().plusDays(15));

        DeudaResponse response = deudoresService.registrarPago(deudaId, java.util.List.of(cash, cheque), userId);

        // Total $500 payment ($200 cash + $300 cheque) covers the entire $500 debt
        assertEquals(0.0, response.getMontoDeuda(), 0.001, "Both cash and cheque reduce debt immediately");
        assertEquals(DebtStatus.PAGADO.name(), response.getEstado());

        // Now annul the cheque: only the $300 cheque portion is restored to debt
        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate = (org.springframework.jdbc.core.JdbcTemplate) org.springframework.test.util.ReflectionTestUtils.getField(deudoresRepository, "jdbcTemplate");
        Long chequeId = jdbcTemplate.queryForObject(
                "SELECT id FROM alertas_cheques WHERE venta_id = ? AND tipo_origen = 'DEUDA_FIADO'",
                Long.class, ventaId);
        assertNotNull(chequeId);

        ventaService.anularCheque(chequeId, userId);
        DeudaResponse postAnnulDebt = deudoresService.getById(deudaId);
        assertEquals(300.0, postAnnulDebt.getMontoDeuda(), 0.001, "Annuling cheque restores only the cheque amount ($300)");
        assertEquals(DebtStatus.PARCIAL.name(), postAnnulDebt.getEstado(), "Debt returns to PARCIAL state because $200 cash remains paid");
    }
}
