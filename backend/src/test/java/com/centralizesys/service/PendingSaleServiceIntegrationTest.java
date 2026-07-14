package com.centralizesys.service;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.model.debt.DeudaResponse;
import com.centralizesys.model.debt.PagoDeudaRequest;
import com.centralizesys.model.sales.DetalleVenta;
import com.centralizesys.model.sales.VentaRequest;
import com.centralizesys.model.sales.VentaResponse;
import com.centralizesys.repository.DeudoresRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

/**
 * Integration Tests for PendingSaleService — Phase 5 TDD.
 *
 * Tests are written FIRST (TDD) to define the exact expected behavior
 * of the partial payment and finalization-with-migration flows.
 */
class PendingSaleServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PendingSaleService pendingSaleService;

    @Autowired
    private VentaService ventaService;

    @Autowired
    private DeudoresRepository deudoresRepository;

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private Long helperCreatePendingSale(Long userId, Long productId, Long quantity, String clientName) {
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(productId);
        item.setCantidad(quantity);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre(clientName);
        request.setItems(List.of(item));
        request.setUsuarioId(userId);

        return pendingSaleService.crearPendiente(request);
    }

    private void helperRegisterDeposit(Long pendingId, Long userId, Double amount, String observations) {
        PagoDeudaRequest pago = new PagoDeudaRequest();
        pago.setMontoPago(amount);
        pago.setMetodoPagoId(1L);
        if (observations != null) {
            pago.setObservaciones(observations);
        }
        pendingSaleService.registrarPago(pendingId, List.of(pago), userId);
    }

    // =========================================================================
    // EXISTING TESTS (Phase 4)
    // =========================================================================

    @Test
    void shouldReserveStockWhenPendingSaleIsCreated() {
        // 1. Arrange
        Long userId = createTestUser();
        Long productId = createTestProduct("PENDING-TEST", 100.0, 50L);

        // 2. Act
        Long pendingId = helperCreatePendingSale(userId, productId, 5L, "Pending Client");

        // 3. Assert
        Long remainingStock = jdbcTemplate.queryForObject(
                "SELECT cantidad FROM stock_por_ubicacion WHERE producto_id = ?", Long.class, productId);
        assertEquals(45L, remainingStock);

        String estado = jdbcTemplate.queryForObject("SELECT estado FROM ventas_pendientes WHERE id = ?", String.class, pendingId);
        assertEquals("PENDIENTE", estado);
    }

    @Test
    void shouldReturnStockWhenPendingSaleIsCanceled() {
        // 1. Arrange
        Long userId = createTestUser();
        Long productId = createTestProduct("CANCEL-TEST", 100.0, 50L);
        Long pendingId = helperCreatePendingSale(userId, productId, 10L, "Cancel Client");

        // 2. Act
        pendingSaleService.cancelarPendiente(pendingId);

        // 3. Assert
        Long restoredStock = jdbcTemplate.queryForObject(
                "SELECT cantidad FROM stock_por_ubicacion WHERE producto_id = ?", Long.class, productId);
        assertEquals(50L, restoredStock);

        String estado = jdbcTemplate.queryForObject("SELECT estado FROM ventas_pendientes WHERE id = ?", String.class, pendingId);
        assertEquals("CANCELADA", estado);
    }

    @Test
    void shouldPerformExactMigrationWhenPendingSaleIsFinalized() {
        // 1. Arrange
        Long userId = createTestUser();
        Long productId = createTestProduct("MIGRATE-TEST", 100.0, 50L);
        Long pendingId = helperCreatePendingSale(userId, productId, 5L, "Migrate Client");

        // Simulate Price Drift
        jdbcTemplate.update("UPDATE productos SET precio_minorista = 200.0 WHERE id = ?", productId);

        // Phase 5 business rule: must register payment before finalizing
        helperRegisterDeposit(pendingId, userId, 500.0, null);

        // 2. Act
        VentaResponse finalizedSale = pendingSaleService.finalizarVenta(pendingId);

        // 3. Assert
        assertEquals(500.0, finalizedSale.getTotalVenta(), 0.001);

        List<DetalleVenta> details = ventaService.getVentaById(finalizedSale.getId()).getItems();
        assertEquals(100.0, details.getFirst().getPrecioUnitario(), 0.001);

        String estado = jdbcTemplate.queryForObject("SELECT estado FROM ventas_pendientes WHERE id = ?", String.class, pendingId);
        assertEquals("FINALIZADA", estado);

        Integer pagosCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pagos_venta WHERE venta_id = ?", Integer.class, finalizedSale.getId());
        assertEquals(1, pagosCount);

        java.time.LocalDateTime migratedFechaPago = jdbcTemplate.queryForObject(
                "SELECT fecha_pago FROM pagos_venta WHERE venta_id = ? LIMIT 1", java.time.LocalDateTime.class, finalizedSale.getId());
        org.junit.jupiter.api.Assertions.assertNotNull(migratedFechaPago, "fecha_pago should have been migrated, but was null.");
    }

    // =========================================================================
    // NEW PHASE 5 TESTS
    // =========================================================================

    @Test
    void shouldInsertPaymentAndUpdateMontoPagadoWhenDepositIsRegistered() {
        // 1. Arrange
        Long userId = createTestUser();
        Long productId = createTestProduct("PAY-TEST-A", 100.0, 50L);
        Long pendingId = helperCreatePendingSale(userId, productId, 5L, "Paying Client"); // Total $500

        // 2. Act: Register $200 deposit
        helperRegisterDeposit(pendingId, userId, 200.0, "Seña inicial");

        // 3. Assert
        Integer paymentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pagos_venta_pendiente WHERE venta_pendiente_id = ? AND anulado = false",
                Integer.class, pendingId);
        assertEquals(1, paymentCount);

        Double storedMonto = jdbcTemplate.queryForObject(
                "SELECT monto FROM pagos_venta_pendiente WHERE venta_pendiente_id = ?",
                Double.class, pendingId);
        assertEquals(200.0, storedMonto, 0.001);

        Double montoPagado = jdbcTemplate.queryForObject(
                "SELECT monto_pagado FROM ventas_pendientes WHERE id = ?",
                Double.class, pendingId);
        assertEquals(200.0, montoPagado, 0.001);

        String estado = jdbcTemplate.queryForObject(
                "SELECT estado FROM ventas_pendientes WHERE id = ?", String.class, pendingId);
        assertEquals("PENDIENTE", estado);
    }

    @Test
    void shouldThrowBusinessRuleExceptionWhenPaymentExceedsBalance() {
        // 1. Arrange
        Long userId = createTestUser();
        Long productId = createTestProduct("PAY-TEST-B", 100.0, 50L);
        Long pendingId = helperCreatePendingSale(userId, productId, 5L, "Overpaying Client"); // Total $500

        // First payment of $200
        helperRegisterDeposit(pendingId, userId, 200.0, null);

        PagoDeudaRequest overpayment = new PagoDeudaRequest();
        overpayment.setMontoPago(400.0);
        overpayment.setMetodoPagoId(1L);

        // 2. Act & Assert: $200 + $400 = $600 > $500
        List<PagoDeudaRequest> pagos = List.of(overpayment);
        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () -> {
            pendingSaleService.registrarPago(pendingId, pagos, userId);
        });
        assertTrue(exception.getMessage().contains("excede"));

        Integer paymentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pagos_venta_pendiente WHERE venta_pendiente_id = ? AND anulado = false",
                Integer.class, pendingId);
        assertEquals(1, paymentCount);
    }

    @Test
    void shouldThrowBusinessRuleExceptionWhenPaymentIsZero() {
        // 1. Arrange
        Long userId = createTestUser();
        Long productId = createTestProduct("PAY-TEST-ZERO", 100.0, 10L);
        Long pendingId = helperCreatePendingSale(userId, productId, 1L, "Zero Pay Client");

        PagoDeudaRequest zeroPago = new PagoDeudaRequest();
        zeroPago.setMontoPago(0.0);
        zeroPago.setMetodoPagoId(1L);

        // 2. Act & Assert
        List<PagoDeudaRequest> pagos = List.of(zeroPago);
        assertThrows(BusinessRuleException.class, () -> {
            pendingSaleService.registrarPago(pendingId, pagos, userId);
        });
    }

    @Test
    void shouldMigratePaymentsAndCreateNoDebtWhenFullyPaid() {
        // 1. Arrange
        Long userId = createTestUser();
        Long productId = createTestProduct("MIGRATE-PAY-A", 100.0, 50L);
        Long pendingId = helperCreatePendingSale(userId, productId, 3L, "Full Pay Client"); // Total $300

        // Fully pay the $300 across 2 deposits
        helperRegisterDeposit(pendingId, userId, 100.0, "Primera cuota");
        helperRegisterDeposit(pendingId, userId, 200.0, "Segunda cuota");

        // 2. Act
        VentaResponse finalizedSale = pendingSaleService.finalizarVenta(pendingId);

        // 3. Assert
        Integer pagosVentaCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pagos_venta WHERE venta_id = ?",
                Integer.class, finalizedSale.getId());
        assertEquals(2, pagosVentaCount);

        Double totalPagado = jdbcTemplate.queryForObject(
                "SELECT SUM(monto) FROM pagos_venta WHERE venta_id = ?",
                Double.class, finalizedSale.getId());
        assertEquals(300.0, totalPagado, 0.001);

        Long deudaCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deudores WHERE venta_id = ?",
                Long.class, finalizedSale.getId());
        assertEquals(0L, deudaCount);

        String estado = jdbcTemplate.queryForObject(
                "SELECT estado FROM ventas_pendientes WHERE id = ?", String.class, pendingId);
        assertEquals("FINALIZADA", estado);
    }

    @Test
    void shouldPreserveDistinctDatesForMultipleDepositsWhenFinalized() {
        // 1. Arrange
        Long userId = createTestUser();
        Long productId = createTestProduct("MIGRATE-DATES", 100.0, 50L);
        Long pendingId = helperCreatePendingSale(userId, productId, 3L, "Dates Client"); // Total $300

        // Deposit 1: Paid yesterday
        jdbcTemplate.update(
                "INSERT INTO pagos_venta_pendiente (venta_pendiente_id, metodo_pago_id, monto, fecha_pago, anulado, usuario_id) " +
                        "VALUES (?, 1, 100.0, NOW() - INTERVAL '1 day', false, ?)", pendingId, userId);
        jdbcTemplate.update("UPDATE ventas_pendientes SET monto_pagado = 100.0 WHERE id = ?", pendingId);

        // Deposit 2: Paid today
        jdbcTemplate.update(
                "INSERT INTO pagos_venta_pendiente (venta_pendiente_id, metodo_pago_id, monto, fecha_pago, anulado, usuario_id) " +
                        "VALUES (?, 1, 200.0, NOW(), false, ?)", pendingId, userId);
        jdbcTemplate.update("UPDATE ventas_pendientes SET monto_pagado = 300.0 WHERE id = ?", pendingId);

        // Fetch original dates
        List<java.time.LocalDateTime> originalDates = jdbcTemplate.query(
                "SELECT fecha_pago FROM pagos_venta_pendiente WHERE venta_pendiente_id = ? ORDER BY monto ASC",
                (rs, rowNum) -> rs.getObject("fecha_pago", java.time.LocalDateTime.class), pendingId);

        // 2. Act
        VentaResponse finalizedSale = pendingSaleService.finalizarVenta(pendingId);

        // 3. Assert
        List<java.time.LocalDateTime> migratedDates = jdbcTemplate.query(
                "SELECT fecha_pago FROM pagos_venta WHERE venta_id = ? ORDER BY monto ASC",
                (rs, rowNum) -> rs.getObject("fecha_pago", java.time.LocalDateTime.class), finalizedSale.getId());

        assertEquals(2, migratedDates.size());

        // Truncate to seconds to avoid precision issues in comparison if any
        java.time.LocalDateTime expected1 = originalDates.getFirst().withNano(0);
        java.time.LocalDateTime migrated1 = migratedDates.getFirst().withNano(0);
        assertEquals(expected1, migrated1, "Yesterday's date must be preserved exactly.");

        java.time.LocalDateTime expected2 = originalDates.get(1).withNano(0);
        java.time.LocalDateTime migrated2 = migratedDates.get(1).withNano(0);
        assertEquals(expected2, migrated2, "Today's date must be preserved exactly.");
    }

    @Test
    void shouldCreateDebtForRemainingBalanceWhenPartiallyPaid() {
        // 1. Arrange
        Long userId = createTestUser();
        Long productId = createTestProduct("MIGRATE-PAY-B", 100.0, 50L);
        Long pendingId = helperCreatePendingSale(userId, productId, 5L, "Partial Pay Client"); // Total $500

        // Pay $200 ($300 remaining)
        helperRegisterDeposit(pendingId, userId, 200.0, "Seña parcial");

        // 2. Act: Finalize with incomplete payment
        VentaResponse finalizedSale = pendingSaleService.finalizarVenta(pendingId);

        // 3. Assert — Payment Migration
        Double totalMigrated = jdbcTemplate.queryForObject(
                "SELECT SUM(monto) FROM pagos_venta WHERE venta_id = ?",
                Double.class, finalizedSale.getId());
        assertEquals(200.0, totalMigrated, 0.001);

        // 3. Assert — Debt Conversion
        List<DeudaResponse> debts = deudoresRepository.findAll();
        DeudaResponse generatedDebt = debts.stream()
                .filter(d -> d.getVentaId().equals(finalizedSale.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Se esperaba un registro de deuda."));

        assertEquals(300.0, generatedDebt.getMontoDeuda(), 0.001);
        assertEquals("Partial Pay Client", generatedDebt.getClienteNombre());
        assertEquals("PENDIENTE", generatedDebt.getEstado());

        String pendingEstado = jdbcTemplate.queryForObject(
                "SELECT estado FROM ventas_pendientes WHERE id = ?", String.class, pendingId);
        assertEquals("FINALIZADA", pendingEstado);
    }

    @Test
    void shouldThrowExceptionWhenFinalizingWithZeroPayments() {
        // 1. Arrange
        Long userId = createTestUser();
        Long productId = createTestProduct("MIGRATE-PAY-ZERO", 100.0, 10L);
        Long pendingId = helperCreatePendingSale(userId, productId, 2L, "Zero Payment Client");

        // 2. Act & Assert: Finalizing with 0 paid is blocked
        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () -> {
            pendingSaleService.finalizarVenta(pendingId);
        });
        assertTrue(exception.getMessage().contains("pago"));
    }

    // =========================================================================
    // NEW TESTS: EDIT CART (modificarCarrito)
    // =========================================================================

    @Test
    void shouldSuccessfullyEditCartAndUpdateTotals() {
        // 1. Arrange
        Long userId = createTestUser();
        Long productId1 = createTestProduct("EDIT-CART-A", 100.0, 50L);
        Long productId2 = createTestProduct("EDIT-CART-B", 200.0, 50L);

        Long pendingId = helperCreatePendingSale(userId, productId1, 5L, "Edit Cart Client"); // Total $500
        helperRegisterDeposit(pendingId, userId, 200.0, "Seña inicial");

        // 2. Act: Edit cart to contain 3 of Prod A ($300) and 2 of Prod B ($400) -> Total $700. Desc = $50. Final = $650
        VentaRequest.ItemRequest itemA = new VentaRequest.ItemRequest();
        itemA.setProductoId(productId1);
        itemA.setCantidad(3L);

        VentaRequest.ItemRequest itemB = new VentaRequest.ItemRequest();
        itemB.setProductoId(productId2);
        itemB.setCantidad(2L);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Edit Cart Client");
        request.setItems(List.of(itemA, itemB));
        request.setDescuentoGlobal(50.0);

        VentaResponse response = pendingSaleService.modificarCarrito(pendingId, request, userId);

        // 3. Assert
        assertEquals(650.0, response.getTotalVenta(), 0.001);
        assertEquals(50.0, response.getDescuentoGlobal(), 0.001);

        // Assert old items are logically deleted and new items are active
        Integer activeItems = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM detalles_venta_pendiente WHERE venta_pendiente_id = ? AND (anulado = false OR anulado IS NULL)", Integer.class, pendingId);
        assertEquals(2, activeItems);

        Integer allItems = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM detalles_venta_pendiente WHERE venta_pendiente_id = ?", Integer.class, pendingId);
        assertEquals(3, allItems); // 1 old (anulado = true) + 2 new (anulado = false)

        Double dbTotal = jdbcTemplate.queryForObject("SELECT total_estimado FROM ventas_pendientes WHERE id = ?", Double.class, pendingId);
        assertEquals(650.0, dbTotal, 0.001);
    }

    @Test
    void shouldThrowExceptionWhenEditingCartWithTotalLessThanPaid() {
        // 1. Arrange
        Long userId = createTestUser();
        Long productId = createTestProduct("EDIT-CART-LESS", 100.0, 50L);

        Long pendingId = helperCreatePendingSale(userId, productId, 5L, "Edit Cart Less Client"); // Total $500
        helperRegisterDeposit(pendingId, userId, 400.0, "Seña grande");

        // 2. Act: Edit cart to contain 2 of Prod A ($200) < Paid ($400)
        VentaRequest.ItemRequest itemA = new VentaRequest.ItemRequest();
        itemA.setProductoId(productId);
        itemA.setCantidad(2L);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Edit Cart Less Client");
        request.setItems(List.of(itemA));

        // 3. Assert
        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () -> {
            pendingSaleService.modificarCarrito(pendingId, request, userId);
        });
        assertTrue(exception.getMessage().contains("menor") || exception.getMessage().contains("pagado"));
    }

    // =========================================================================
    // NEW TESTS: VOID PAYMENT (anularPago)
    // =========================================================================

    @Test
    void shouldVoidPaymentAndDecrementMontoPagado() {
        // 1. Arrange
        Long userId = createTestUser();
        Long productId = createTestProduct("VOID-PAY", 100.0, 50L);

        Long pendingId = helperCreatePendingSale(userId, productId, 5L, "Void Pay Client"); // Total $500
        helperRegisterDeposit(pendingId, userId, 200.0, "Seña inicial");

        Long pagoId = jdbcTemplate.queryForObject("SELECT id FROM pagos_venta_pendiente WHERE venta_pendiente_id = ? LIMIT 1", Long.class, pendingId);

        // 2. Act
        pendingSaleService.anularPago(pendingId, pagoId, userId);

        // 3. Assert
        Boolean anulado = jdbcTemplate.queryForObject("SELECT anulado FROM pagos_venta_pendiente WHERE id = ?", Boolean.class, pagoId);
        assertTrue(anulado);

        Double montoPagado = jdbcTemplate.queryForObject("SELECT monto_pagado FROM ventas_pendientes WHERE id = ?", Double.class, pendingId);
        assertEquals(0.0, montoPagado, 0.001);
    }
    // =========================================================================
    // NEW TESTS: EDGE CASES & GUARDS
    // =========================================================================

    @Test
    void shouldThrowExceptionWhenEditingOrFinalizingNonPendingSale() {
        Long userId = createTestUser();
        Long productId = createTestProduct("GUARD-TEST", 100.0, 50L);
        Long pendingId = helperCreatePendingSale(userId, productId, 5L, "Guard Client");

        // Cancel the sale so it's not PENDIENTE
        pendingSaleService.cancelarPendiente(pendingId);

        // Try to pay
        BusinessRuleException exPay = assertThrows(BusinessRuleException.class, () -> {
            helperRegisterDeposit(pendingId, userId, 100.0, null);
        });
        assertTrue(exPay.getMessage().toLowerCase().contains("estado") || exPay.getMessage().toLowerCase().contains("pendiente"));

        // Try to finalize
        BusinessRuleException exFinalize = assertThrows(BusinessRuleException.class, () -> {
            pendingSaleService.finalizarVenta(pendingId);
        });
        assertTrue(exFinalize.getMessage().toLowerCase().contains("estado") || exFinalize.getMessage().toLowerCase().contains("pendiente"));

        // Try to modify
        VentaRequest.ItemRequest itemEdit = new VentaRequest.ItemRequest();
        itemEdit.setProductoId(productId);
        itemEdit.setCantidad(1L);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("New Name");
        request.setItems(List.of(itemEdit));

        BusinessRuleException exEdit = assertThrows(BusinessRuleException.class, () -> {
            pendingSaleService.modificarCarrito(pendingId, request, userId);
        });
        assertTrue(exEdit.getMessage().toLowerCase().contains("estado") || exEdit.getMessage().toLowerCase().contains("pendiente"));
    }

    @Test
    void shouldThrowExceptionWhenDiscountIsNegativeOrExceedsSubtotal() {
        Long userId = createTestUser();
        Long productId = createTestProduct("DISC-TEST", 100.0, 50L);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(productId);
        item.setCantidad(2L); // Subtotal: 200

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Discount Client");
        request.setItems(List.of(item));
        request.setUsuarioId(userId);

        // Negative discount
        request.setDescuentoGlobal(-10.0);
        BusinessRuleException exNeg = assertThrows(BusinessRuleException.class, () -> {
            pendingSaleService.crearPendiente(request);
        });
        assertTrue(exNeg.getMessage().toLowerCase().contains("descuento"));

        // Exceeds subtotal
        request.setDescuentoGlobal(250.0);
        BusinessRuleException exExc = assertThrows(BusinessRuleException.class, () -> {
            pendingSaleService.crearPendiente(request);
        });
        assertTrue(exExc.getMessage().toLowerCase().contains("descuento") || exExc.getMessage().toLowerCase().contains("excede"));
    }

    @Test
    void shouldThrowExceptionWhenCartIsEmptyOrNull() {
        Long userId = createTestUser();

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Empty Client");
        request.setUsuarioId(userId);

        // Null items
        BusinessRuleException exNull = assertThrows(BusinessRuleException.class, () -> {
            pendingSaleService.crearPendiente(request);
        });
        assertTrue(exNull.getMessage().toLowerCase().contains("producto"));

        // Empty items
        request.setItems(List.of());
        BusinessRuleException exEmpty = assertThrows(BusinessRuleException.class, () -> {
            pendingSaleService.crearPendiente(request);
        });
        assertTrue(exEmpty.getMessage().toLowerCase().contains("producto"));
    }
}
