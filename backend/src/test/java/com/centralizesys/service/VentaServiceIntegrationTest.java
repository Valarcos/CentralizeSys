package com.centralizesys.service;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.sales.VentaRequest;
import com.centralizesys.model.sales.VentaResponse;
import com.centralizesys.model.debt.PagoDeudaRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// 1. EXTENDS BaseIntegrationTest (Inherits config, transaction rollback, and common repos)
class VentaServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private VentaService ventaService;

    // Note: productRepository, stockRepository, jdbcTemplate are inherited from Base!

    private Long testProductId;
    private Long testUserId;

    @BeforeEach
    void setupData() {
        // 2. USE HELPERS
        this.testUserId = createTestUser();
        this.testProductId = createTestProduct("TEST-CODE", 100.0, 100L); // Price $100, Stock 100
    }

    @Test
    @DisplayName("IT-01: Transaction commits when successful")
    void transaction_Commits_WhenSuccessful() {
        // Arrange
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(testProductId);
        item.setCantidad(10L);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Integration Client");
        request.setItems(List.of(item));
        request.setUsuarioId(testUserId);

        // Act
        VentaResponse response = ventaService.registrarVenta(request);

        // Assert
        assertNotNull(response.getId());

        // Verify Data in DB via JDBC (Inherited from Base)
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ventas WHERE id = ?", Integer.class, response.getId());
        assertEquals(1, count, "Venta header should exist");

        // Verify Stock Decrement (100 - 10 = 90)
        Long remainingStock = jdbcTemplate.queryForObject(
                "SELECT cantidad FROM stock_por_ubicacion WHERE producto_id = ?", Long.class, testProductId);
        assertEquals(90L, remainingStock);
    }

    @Test
    @DisplayName("IT-02: Transaction rolls back on failure (Atomicity)")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void transaction_RollsBack_OnFailure() {
        // Arrange
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(testProductId);
        item.setCantidad(10L);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre(null); // Valid for Header in DB, but we break the Payment FK below
        request.setItems(List.of(item));

        // Force failure: Non-existent Payment Method ID
        VentaRequest.PagoRequest invalidPago = new VentaRequest.PagoRequest();
        invalidPago.setMetodoPagoId(9999L);
        invalidPago.setMonto(100.0);

        request.setPagos(List.of(invalidPago));

        // Act & Assert
        // Since Phase 4.1, the service validates the payment method ID *before* any SQL insert.
        // A non-existent payment method ID now throws BusinessRuleException at the service layer.
        // The transaction still rolls back atomically — the Venta header must NOT be persisted.
        assertThrows(BusinessRuleException.class, () -> ventaService.registrarVenta(request));

        // PROOF that rollback worked:
        // Since we are NOT in a test transaction, we can see the real DB state committed by the Service.
        // If rollback worked, the header (saved before the error) should be GONE.
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ventas", Integer.class);
        assertEquals(0, count, "Transaction rollback failed: Venta header persisted despite exception");
    }

    @Test
    @DisplayName("IT-03: Persistence verifies Foreign Keys")
    void transaction_Aborts_WhenProductNotFound_BeforeInsert() {
        // Arrange: Try to sell a non-existent product ID
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(9999L);
        item.setCantidad(1L);

        VentaRequest request = new VentaRequest();
        request.setItems(List.of(item));

        // Act & Assert
        // [CHANGED]
        assertThrows(ResourceNotFoundException.class, () -> ventaService.registrarVenta(request));
    }

    @Test
    @DisplayName("IT-04: Schema allows Null User ID (Robustness)")
    void persistence_HandlesNullUserId() {
        // Arrange
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(testProductId);
        item.setCantidad(1L);

        VentaRequest.PagoRequest pago = new VentaRequest.PagoRequest();
        pago.setMetodoPagoId(1L);
        pago.setMonto(100.0);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Consumidor Final");
        request.setItems(List.of(item));
        request.setPagos(List.of(pago));
        request.setUsuarioId(null); // Explicit Null

        // Act
        VentaResponse response = ventaService.registrarVenta(request);

        // Assert
        assertNotNull(response.getId());

        Map<String, Object> result = jdbcTemplate.queryForMap("SELECT usuario_id FROM ventas WHERE id = ?", response.getId());
        assertNull(result.get("usuario_id"));
    }

    @Test
    @DisplayName("IT-05: SQLite Trigger updates Global Stock")
    void integration_StockConcurrency_CheckTrigger() {
        // Arrange
        // Verify Helper created correct state (Product table should have 100 stock via trigger)
        Long initialGlobalStock = jdbcTemplate.queryForObject(
                "SELECT cantidad_stock FROM productos WHERE id = ?", Long.class, testProductId);
        assertEquals(100L, initialGlobalStock, "Trigger should have set initial stock");

        // Act: Sell 5 items
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(testProductId);
        item.setCantidad(5L);

        VentaRequest.PagoRequest pago = new VentaRequest.PagoRequest();
        pago.setMetodoPagoId(1L);
        pago.setMonto(500.0);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Consumidor Final");
        request.setItems(List.of(item));
        request.setPagos(List.of(pago));
        Long localTestUserId = createTestUser();
        request.setUsuarioId(localTestUserId);
        ventaService.registrarVenta(request);

        // Assert
        // Verify PRODUCTS table (updated by DB Trigger), not just stock_por_ubicacion
        Long finalGlobalStock = jdbcTemplate.queryForObject(
                "SELECT cantidad_stock FROM productos WHERE id = ?", Long.class, testProductId);

        assertEquals(95L, finalGlobalStock, "Trigger 'update_stock_after_update' failed to fire");
    }

    @Test
    @DisplayName("IT-06: Round Trip (Write -> Read) verifies Mappers")
    void read_GetVentaById_ReturnsFullTree() {
        // Arrange
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(testProductId);
        item.setCantidad(2L); // 2 * 100 = 200

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Round Trip Client");
        request.setItems(List.of(item));
        request.setUsuarioId(testUserId);
        // Payment
        VentaRequest.PagoRequest pago = new VentaRequest.PagoRequest();
        pago.setMetodoPagoId(1L);
        pago.setMonto(200.0);
        request.setPagos(List.of(pago));

        // Act 1: Write
        VentaResponse written = ventaService.registrarVenta(request);
        Long id = written.getId();

        // Act 2: Read (The untested path)
        VentaResponse read = ventaService.getVentaById(id);

        // Assert
        assertEquals(id, read.getId());
        assertEquals("Round Trip Client", read.getClienteNombre());
        assertEquals(200.0, read.getTotalVenta());
        assertEquals(1, read.getItems().size());
        assertEquals(1, read.getPagos().size());

        // Verify Detail Mapper
        assertEquals(testProductId, read.getItems().getFirst().getProductoId());
        assertEquals(200.0, read.getItems().getFirst().getSubtotal());
    }

    @Test
    @DisplayName("IT-07: Edge Case - Standard sale properly sets CURRENT_TIMESTAMP for fecha_pago")
    void transaction_SetsCurrentTimestampForFechaPago_OnStandardSale() {
        // Arrange
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(testProductId);
        item.setCantidad(1L);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Timestamp Client");
        request.setItems(List.of(item));
        request.setUsuarioId(testUserId);

        VentaRequest.PagoRequest pago = new VentaRequest.PagoRequest();
        pago.setMetodoPagoId(1L);
        pago.setMonto(100.0);
        request.setPagos(List.of(pago));

        // Act
        VentaResponse response = ventaService.registrarVenta(request);

        // Assert
        assertNotNull(response.getId());

        // We specifically check the database directly because the response might just map whatever the DB returned
        java.time.LocalDateTime dbDate = jdbcTemplate.queryForObject(
                "SELECT fecha_pago FROM pagos_venta WHERE venta_id = ? LIMIT 1",
                java.time.LocalDateTime.class, response.getId());

        assertNotNull(dbDate, "fecha_pago should NOT be null, COALESCE should have set it to CURRENT_TIMESTAMP");

        // Assert that the date is approximately now (within 1 minute)
        assertTrue(dbDate.isAfter(java.time.LocalDateTime.now().minusMinutes(1)));
        assertTrue(dbDate.isBefore(java.time.LocalDateTime.now().plusMinutes(1)));
    }

    @Test
    @DisplayName("IT-08: Pending Sale Lifecycle - Creation and Finalization do not double-discount stock")
    void integration_PendingSale_FullLifecycle() {
        // Arrange
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(testProductId);
        item.setCantidad(10L);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Pending Lifecycle");
        request.setItems(List.of(item));
        // Act 1: Registrar PENDIENTE
        Long pendingId = ventaService.crearPendiente(request, testUserId);
        assertNotNull(pendingId);

        VentaResponse pending = ventaService.getVentaById(pendingId);
        assertEquals("PENDIENTE", pending.getEstado());

        // Assert 1: Stock is discounted ONCE
        Long stockAfterPending = jdbcTemplate.queryForObject(
                "SELECT cantidad_stock FROM productos WHERE id = ?", Long.class, testProductId);
        assertEquals(90L, stockAfterPending, "Stock should decrease by 10 upon pending creation");

        // Act 2: Agregar un pago (seña) y Finalizar Venta
        PagoDeudaRequest pagoReq = new PagoDeudaRequest();
        pagoReq.setMetodoPagoId(1L);
        pagoReq.setMontoPago(500.0);
        ventaService.registrarPago(pendingId, List.of(pagoReq), testUserId);

        ventaService.finalizarVenta(pending.getId(), testUserId);

        // Assert 2: Status is ACTIVA, stock remains the same (no double discount)
        VentaResponse finalized = ventaService.getVentaById(pending.getId());
        assertEquals("ACTIVA", finalized.getEstado());

        Long stockAfterFinalize = jdbcTemplate.queryForObject(
                "SELECT cantidad_stock FROM productos WHERE id = ?", Long.class, testProductId);
        assertEquals(90L, stockAfterFinalize, "Stock MUST NOT be discounted again on finalization");

        // Assert 3: Date shifted (fecha updated to now)
        java.time.LocalDateTime dbFecha = jdbcTemplate.queryForObject(
                "SELECT fecha FROM ventas WHERE id = ?", java.time.LocalDateTime.class, pending.getId());
        assertTrue(dbFecha.isAfter(java.time.LocalDateTime.now().minusMinutes(1)));
    }

    @Test
    @DisplayName("IT-09: Pending Sale Cancellation restores stock")
    void integration_PendingSale_Cancellation() {
        // Arrange
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(testProductId);
        item.setCantidad(5L);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Pending Cancel");
        request.setItems(List.of(item));
        Long pendingId = ventaService.crearPendiente(request, testUserId);

        Long stockAfterPending = jdbcTemplate.queryForObject(
                "SELECT cantidad_stock FROM productos WHERE id = ?", Long.class, testProductId);
        assertEquals(95L, stockAfterPending, "Stock dropped by 5");

        // Act: Cancelar
        ventaService.cancelarPendiente(pendingId, testUserId);

        // Assert: Status is CANCELADA_PENDIENTE, stock is restored
        String estado = jdbcTemplate.queryForObject(
                "SELECT estado FROM ventas WHERE id = ?", String.class, pendingId);
        assertEquals("CANCELADA_PENDIENTE", estado);

        Long stockRestored = jdbcTemplate.queryForObject(
                "SELECT cantidad_stock FROM productos WHERE id = ?", Long.class, testProductId);
        assertEquals(100L, stockRestored, "Stock MUST be restored upon cancellation");
    }

    @Test
    @DisplayName("IT-09: modificarCarrito performs atomic delta updates on payments")
    void modificarCarrito_PerformsAtomicDeltaUpdates() {
        // Arrange
        // Create a pending sale with 1 payment and 1 cheque
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(testProductId);
        item.setCantidad(1L);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Pending Client");
        request.setItems(List.of(item));
        request.setUsuarioId(testUserId);

        Long cashMethodId = 1L; // Assuming cash is 1 (efectivo)

        VentaRequest.PagoRequest p1 = new VentaRequest.PagoRequest();
        p1.setMetodoPagoId(cashMethodId);
        p1.setMonto(50.0);
        request.setPagos(List.of(p1));

        com.centralizesys.model.cheque.AlertaChequeRequest c1 = new com.centralizesys.model.cheque.AlertaChequeRequest();
        c1.setMonto(50.0);
        c1.setFechaCobro(java.time.LocalDate.now().plusDays(10));
        request.setCheques(List.of(c1));

        Long pendingId = ventaService.crearPendiente(request, testUserId);

        // Retrieve generated IDs for payments/cheques
        Long oldPagoId = jdbcTemplate.queryForObject("SELECT id FROM pagos_venta WHERE venta_id = ? AND anulado = false LIMIT 1", Long.class, pendingId);
        Long oldChequeId = jdbcTemplate.queryForObject("SELECT id FROM alertas_cheques WHERE venta_id = ? AND estado = 'PENDIENTE' LIMIT 1", Long.class, pendingId);

        // Act - Modificar carrito: Keep the payment, delete the cheque, add a new payment
        VentaRequest modifyRequest = new VentaRequest();
        modifyRequest.setClienteNombre("Pending Client");
        modifyRequest.setItems(List.of(item));

        VentaRequest.PagoRequest pKeep = new VentaRequest.PagoRequest();
        pKeep.setId(oldPagoId); // Important: pass the ID to keep it
        pKeep.setMetodoPagoId(cashMethodId);
        pKeep.setMonto(50.0);

        VentaRequest.PagoRequest pNew = new VentaRequest.PagoRequest();
        pNew.setMetodoPagoId(cashMethodId);
        pNew.setMonto(50.0);

        modifyRequest.setPagos(List.of(pKeep, pNew));
        // Omit cheques to trigger deletion of oldChequeId

        ventaService.modificarCarrito(pendingId, modifyRequest, testUserId);

        // Assert
        // The old payment should still be active
        Boolean pagoAnulado = jdbcTemplate.queryForObject("SELECT anulado FROM pagos_venta WHERE id = ?", Boolean.class, oldPagoId);
        assertFalse(pagoAnulado, "Old payment should be preserved (anulado = false)");

        // The old cheque should be anulado
        String chequeEstado = jdbcTemplate.queryForObject("SELECT estado FROM alertas_cheques WHERE id = ?", String.class, oldChequeId);
        assertEquals("ANULADA", chequeEstado, "Omitted old cheque should be marked ANULADA");

        // There should be a total of 2 active payments now
        Integer activePaymentsCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pagos_venta WHERE venta_id = ? AND anulado = false", Integer.class, pendingId);
        assertEquals(2, activePaymentsCount, "Should have 2 active payments");
    }

    @Test
    @DisplayName("IT-10: Rejection of Negative Payments")
    void transaction_RollsBack_OnNegativePayment() {
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(testProductId);
        item.setCantidad(1L);

        VentaRequest.PagoRequest pago = new VentaRequest.PagoRequest();
        pago.setMetodoPagoId(1L);
        pago.setMonto(-100.0); // Negative payment

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Negative Payer");
        request.setItems(List.of(item));
        request.setPagos(List.of(pago));
        request.setUsuarioId(testUserId);

        assertThrows(BusinessRuleException.class, () -> ventaService.registrarVenta(request));

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ventas", Integer.class);
        assertEquals(0, count, "Transaction should be rolled back/aborted");
    }

    @Test
    @DisplayName("IT-11: Rejection of Zero Payments")
    void transaction_RollsBack_OnZeroPayment() {
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(testProductId);
        item.setCantidad(1L);

        VentaRequest.PagoRequest pago = new VentaRequest.PagoRequest();
        pago.setMetodoPagoId(1L);
        pago.setMonto(0.0); // Zero payment

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Zero Payer");
        request.setItems(List.of(item));
        request.setPagos(List.of(pago));
        request.setUsuarioId(testUserId);

        // VentaService's validatePagos doesn't currently strictly throw for 0 in registrarVenta,
        // but if it is configured to reject 0 (or if we enforce it), this should pass.
        // If it allows 0, we can adjust the expectation, but zero payments shouldn't be valid.
        assertThrows(BusinessRuleException.class, () -> ventaService.registrarVenta(request));
    }

    @Test
    @DisplayName("IT-12: Rejection of Negative Sales Quantities")
    void transaction_RollsBack_OnNegativeQuantity() {
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(testProductId);
        item.setCantidad(-5L); // Negative quantity

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Negative Buyer");
        request.setItems(List.of(item));
        request.setUsuarioId(testUserId);

        assertThrows(BusinessRuleException.class, () -> ventaService.registrarVenta(request));
    }

    @Test
    @DisplayName("IT-13: Rejection of Negative Discount")
    void transaction_RollsBack_OnNegativeDiscount() {
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(testProductId);
        item.setCantidad(1L);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Discount Tester");
        request.setItems(List.of(item));
        request.setDescuentoGlobal(-50.0); // Negative discount
        request.setUsuarioId(testUserId);

        assertThrows(BusinessRuleException.class, () -> ventaService.registrarVenta(request));
    }

    @Test
    @DisplayName("IT-14: Rejection of Negative Surcharge")
    void transaction_RollsBack_OnNegativeSurcharge() {
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(testProductId);
        item.setCantidad(1L);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Surcharge Tester");
        request.setItems(List.of(item));
        request.setRecargoGlobal(-20.0); // Negative surcharge
        request.setUsuarioId(testUserId);

        assertThrows(BusinessRuleException.class, () -> ventaService.registrarVenta(request));
    }

    @Test
    @DisplayName("IT-15: Rejection of Negative Saldo Generado")
    void transaction_RollsBack_OnNegativeSaldoGenerado() {
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(testProductId);
        item.setCantidad(1L);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Saldo Tester");
        request.setItems(List.of(item));
        request.setSaldoGenerado(-100.0); // Negative saldo generado
        request.setUsuarioId(testUserId);

        assertThrows(BusinessRuleException.class, () -> ventaService.registrarVenta(request));
    }
}