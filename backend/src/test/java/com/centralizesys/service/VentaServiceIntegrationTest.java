package com.centralizesys.service;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.sales.VentaRequest;
import com.centralizesys.model.sales.VentaResponse;
import com.centralizesys.repository.VentaRepository; // Specific to this test
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// 1. EXTENDS BaseIntegrationTest (Inherits config, transaction rollback, and common repos)
class VentaServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private VentaService ventaService;

    @Autowired
    private VentaRepository ventaRepository;

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
    @DirtiesContext// Forces a clean DB state after this test to verify rollback reliably
    @DisplayName("IT-02: Transaction rolls back on failure (Atomicity)")
    void transaction_RollsBack_OnFailure() {
        // Arrange
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(testProductId);
        item.setCantidad(10L);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Rollback Client"); // Valid for Header in DB, but we break the Payment FK below
        request.setItems(List.of(item));

        // Force failure: Non-existent Payment Method ID
        VentaRequest.PagoRequest invalidPago = new VentaRequest.PagoRequest();
        invalidPago.setMetodoPagoId(9999L);
        invalidPago.setMonto(100.0);

        request.setPagos(List.of(invalidPago));

        // Act & Assert
        assertThrows(org.springframework.dao.DataAccessException.class, () -> ventaService.registrarVenta(request));

        // CRITICAL ASSERTION: Rollback check
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ventas", Integer.class);
        assertEquals(0, count, "Venta table should be empty due to rollback");
    }

    @Test
    @DisplayName("IT-03: Persistence verifies Foreign Keys")
    void persistence_VerifiesForeignKeys() {
        // Arrange: Try to sell a non-existent product ID
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(9999L);
        item.setCantidad(1L);

        VentaRequest request = new VentaRequest();
        request.setItems(List.of(item));

        // Act & Assert
        // This confirms SQLiteConfig.enforceForeignKeys(true) is working
        assertThrows(RuntimeException.class, () -> ventaService.registrarVenta(request));
    }

    @Test
    @DisplayName("IT-04: Schema allows Null User ID (Robustness)")
    void persistence_HandlesNullUserId() {
        // Arrange
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(testProductId);
        item.setCantidad(1L);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("No User Client");
        request.setItems(List.of(item));
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

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Stock Test Client");
        request.setItems(List.of(item));
        ventaService.registrarVenta(request);

        // Assert
        // Verify PRODUCTS table (updated by DB Trigger), not just stock_por_ubicacion
        Long finalGlobalStock = jdbcTemplate.queryForObject(
                "SELECT cantidad_stock FROM productos WHERE id = ?", Long.class, testProductId);

        assertEquals(95L, finalGlobalStock, "Trigger 'update_stock_after_update' failed to fire");
    }
}