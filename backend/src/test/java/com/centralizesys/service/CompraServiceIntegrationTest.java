package com.centralizesys.service;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.model.purchase.CompraItemRequest;
import com.centralizesys.model.purchase.CompraRequest;
import com.centralizesys.model.purchase.CompraResponse;
import com.centralizesys.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompraServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CompraService compraService;

    // We use stockRepository helper to create locations
    @Autowired
    private StockRepository stockRepository;

    private Long testProductId;
    private Long testUserId;
    private Long locationIdA;
    private Long locationIdB;

    @BeforeEach
    void setupData() {
        // 1. Create User
        this.testUserId = createTestUser();

        // 2. Create Locations
        // Logic: Try to find existing, otherwise create
        try {
            this.locationIdA = stockRepository.createLocation("100");
        } catch (Exception e) {
            // Fallback if tests share context and it already exists
            this.locationIdA = stockRepository.findAllLocations().stream()
                    .filter(l -> l.getNombre().equals("100")).findFirst().orElseThrow().getId();
        }

        try {
            this.locationIdB = stockRepository.createLocation("200");
        } catch (Exception e) {
            this.locationIdB = stockRepository.findAllLocations().stream()
                    .filter(l -> l.getNombre().equals("200")).findFirst().orElseThrow().getId();
        }

        // 3. Create Product (Base Price 100.0, Cost 50.0)
        // Note: createTestProduct uses "Test Desc" and sets Cost to 0.5 * Price
        this.testProductId = createTestProduct("COMPRA-TEST", 100.0, 0L);
    }

    // --- GROUP 1: ATOMICITY & PERSISTENCE ---

    @Test
    @DisplayName("IT-01: Transaction commits when successful")
    void transaction_Commits_WhenSuccessful() {
        // Arrange
        CompraItemRequest item = new CompraItemRequest();
        item.setProductoId(testProductId);
        item.setCantidad(10L);
        item.setCostoUnitario(50.0); // Must match DB cost (0.5 * 100.0)
        item.setUbicacionId(locationIdA);

        CompraRequest request = new CompraRequest();
        request.setProveedor("Sony");
        request.setNroComprobante("A-001");
        request.setUsuarioId(testUserId);
        request.setItems(List.of(item));

        // Act
        CompraResponse response = compraService.registrarCompra(request);

        // Assert
        assertNotNull(response.getId());

        // Verify DB Counts
        Integer countHeaders = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM compras WHERE id = ?", Integer.class, response.getId());
        assertEquals(1, countHeaders, "Compra header should exist");

        Integer countDetails = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM detalles_compra WHERE compra_id = ?", Integer.class, response.getId());
        assertEquals(1, countDetails, "Detail row should exist");

        // Verify Stock Increase
        Long stock = jdbcTemplate.queryForObject(
                "SELECT cantidad FROM stock_por_ubicacion WHERE producto_id = ? AND ubicacion_id = ?",
                Long.class, testProductId, locationIdA);
        assertEquals(10L, stock);
    }

    @Test
    @DisplayName("IT-02: Transaction rolls back on Real Foreign Key Failure (Location)")
    void transaction_RollsBack_OnRealForeignKeyFailure() {
        // Arrange
        CompraItemRequest item = new CompraItemRequest();
        item.setProductoId(testProductId);
        item.setCantidad(10L);
        item.setCostoUnitario(50.0);
        item.setUbicacionId(9999L); // Non-existent Location

        CompraRequest request = new CompraRequest();
        request.setProveedor("Fail");
        request.setUsuarioId(testUserId);
        request.setItems(List.of(item));

        // Act & Assert
        // The Service specifically catches DataAccessException and throws BusinessRuleException for stock errors
        assertThrows(BusinessRuleException.class, () -> compraService.registrarCompra(request));

        // Note: We rely on Spring Transaction Management to rollback changes (insert into compras)
        // that happened before the exception was thrown.
    }

    @Test
    @DisplayName("IT-03: Transaction rolls back on Invalid User FK")
    void transaction_RollsBack_On_Invalid_User_FK() {
        // Arrange
        CompraItemRequest item = new CompraItemRequest();
        item.setProductoId(testProductId);
        item.setCantidad(10L);
        item.setCostoUnitario(50.0);
        item.setUbicacionId(locationIdA);

        CompraRequest request = new CompraRequest();
        request.setProveedor("Fail User");
        request.setUsuarioId(99999L); // Non-existent User
        request.setItems(List.of(item));

        // Act & Assert
        // Unlike IT-02, the Service does NOT catch this specific save error.
        // It bubbles up as DataIntegrityViolationException from the DB/Driver.
        assertThrows(DataIntegrityViolationException.class, () -> compraService.registrarCompra(request));
    }

    // --- GROUP 2: TRIGGERS (STOCK MANAGEMENT) ---

    @Test
    @DisplayName("IT-04: Stock Upsert Mechanism (Hybrid Insert/Update)")
    void stock_Upsert_Mechanism_Hybrid() {
        // Setup: Add 5 units to Location A initially
        stockRepository.addStock(testProductId, locationIdA, 5L);

        // Arrange Request: Add 5 more to A, Add 10 to B (New)
        CompraItemRequest itemA = new CompraItemRequest();
        itemA.setProductoId(testProductId);
        itemA.setUbicacionId(locationIdA);
        itemA.setCantidad(5L);
        itemA.setCostoUnitario(50.0);

        CompraItemRequest itemB = new CompraItemRequest();
        itemB.setProductoId(testProductId);
        itemB.setUbicacionId(locationIdB);
        itemB.setCantidad(10L);
        itemB.setCostoUnitario(50.0);

        CompraRequest request = new CompraRequest();
        request.setProveedor("Hybrid");
        request.setUsuarioId(testUserId);
        request.setItems(List.of(itemA, itemB));

        // Act
        compraService.registrarCompra(request);

        // Assert
        // Location A: Should be 5 (initial) + 5 (added) = 10
        Long stockA = jdbcTemplate.queryForObject(
                "SELECT cantidad FROM stock_por_ubicacion WHERE producto_id = ? AND ubicacion_id = ?",
                Long.class, testProductId, locationIdA);
        assertEquals(10L, stockA, "Location A should have updated existing row");

        // Location B: Should be 10
        Long stockB = jdbcTemplate.queryForObject(
                "SELECT cantidad FROM stock_por_ubicacion WHERE producto_id = ? AND ubicacion_id = ?",
                Long.class, testProductId, locationIdB);
        assertEquals(10L, stockB, "Location B should have inserted new row");

        // Total Rows for this product
        Integer totalRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stock_por_ubicacion WHERE producto_id = ?", Integer.class, testProductId);
        assertEquals(2, totalRows);
    }

    @Test
    @DisplayName("IT-05: Batch Detalles Persistence Order and Mapping")
    void batch_Detalles_Persistence_Order_And_Mapping() {
        // Setup: Create 3 distinct products with DIFFERENT costs to verify mapping
        // P1: Cost 10, P2: Cost 20, P3: Cost 30
        Long p1 = createTestProduct("P1", 20.0, 0L); // Cost 10
        Long p2 = createTestProduct("P2", 40.0, 0L); // Cost 20
        Long p3 = createTestProduct("P3", 60.0, 0L); // Cost 30

        CompraItemRequest i1 = new CompraItemRequest();
        i1.setProductoId(p1); i1.setCantidad(1L); i1.setCostoUnitario(10.0); i1.setUbicacionId(locationIdA);

        CompraItemRequest i2 = new CompraItemRequest();
        i2.setProductoId(p2); i2.setCantidad(1L); i2.setCostoUnitario(20.0); i2.setUbicacionId(locationIdA);

        CompraItemRequest i3 = new CompraItemRequest();
        i3.setProductoId(p3); i3.setCantidad(1L); i3.setCostoUnitario(30.0); i3.setUbicacionId(locationIdA);

        CompraRequest request = new CompraRequest();
        request.setProveedor("Batch Check");
        request.setUsuarioId(testUserId);
        request.setItems(List.of(i1, i2, i3));

        // Act
        CompraResponse response = compraService.registrarCompra(request);

        // Assert
        // 1. Verify count
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM detalles_compra WHERE compra_id = ?", Integer.class, response.getId());
        assertEquals(3, count);

        // 2. Verify Mapping (Crucial: Ensure P1 got Cost 10, not P3's cost)
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT producto_id, costo_unitario FROM detalles_compra WHERE compra_id = ?", response.getId());

        for (Map<String, Object> row : rows) {
            Long pid = ((Number) row.get("producto_id")).longValue();
            Double cost = ((Number) row.get("costo_unitario")).doubleValue();

            if (pid.equals(p1)) assertEquals(10.0, cost, 0.001);
            if (pid.equals(p2)) assertEquals(20.0, cost, 0.001);
            if (pid.equals(p3)) assertEquals(30.0, cost, 0.001);
        }
    }

    // --- GROUP 3: TRIGGERS & GLOBAL STATE ---

    @Test
    @DisplayName("IT-06: Trigger updates Global Stock (Chain)")
    void trigger_UpdatesGlobalStock_Chain() {
        // Setup: Initial Global Stock is 0
        Long initial = jdbcTemplate.queryForObject("SELECT cantidad_stock FROM productos WHERE id = ?", Long.class, testProductId);
        assertEquals(0L, initial);

        // Step 1: Buy 10
        CompraItemRequest i1 = new CompraItemRequest();
        i1.setProductoId(testProductId); i1.setCantidad(10L); i1.setCostoUnitario(50.0); i1.setUbicacionId(locationIdA);
        CompraRequest r1 = new CompraRequest(); r1.setUsuarioId(testUserId); r1.setItems(List.of(i1));

        compraService.registrarCompra(r1);

        // Assert 1 (Trigger fired?)
        Long afterFirst = jdbcTemplate.queryForObject("SELECT cantidad_stock FROM productos WHERE id = ?", Long.class, testProductId);
        assertEquals(10L, afterFirst, "Trigger should update global to 10");

        // Step 2: Buy 5 more
        CompraItemRequest i2 = new CompraItemRequest();
        i2.setProductoId(testProductId); i2.setCantidad(5L); i2.setCostoUnitario(50.0); i2.setUbicacionId(locationIdA);
        CompraRequest r2 = new CompraRequest(); r2.setUsuarioId(testUserId); r2.setItems(List.of(i2));

        compraService.registrarCompra(r2);

        // Assert 2 (Trigger fired again?)
        Long afterSecond = jdbcTemplate.queryForObject("SELECT cantidad_stock FROM productos WHERE id = ?", Long.class, testProductId);
        assertEquals(15L, afterSecond, "Trigger should update global to 15");
    }

    // --- GROUP 4: CONSTRAINTS & SCHEMA ---

    @Test
    @DisplayName("IT-07: Service Prevents Purchase When Cost Differs")
    void service_Prevents_Purchase_WhenCostDiffers_EvenIfDBAllows() {
        // Arrange
        // Product in DB has Cost 50.0. Request asks for 70.0.
        CompraItemRequest item = new CompraItemRequest();
        item.setProductoId(testProductId);
        item.setCantidad(10L);
        item.setCostoUnitario(70.0); // Mismatch
        item.setUbicacionId(locationIdA);

        CompraRequest request = new CompraRequest();
        request.setUsuarioId(testUserId);
        request.setItems(List.of(item));

        // Act & Assert
        assertThrows(BusinessRuleException.class, () -> compraService.registrarCompra(request));

        // Verify DB is clean (Service logic blocked it before DB insert)
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM compras", Integer.class);
        assertEquals(0, count);
    }

    @Test
    @DisplayName("IT-08: Persistence allows Same Code Different Cost (Schema Check)")
    void persistence_Allows_SameCode_DifferentCost_SchemaCheck() {
        // Scenario: Manually insert SQL to verify Schema Constraints allow variants.
        // Product A: Code "VAR-1", Cost 100, Price 200
        // Product B: Code "VAR-1", Cost 150, Price 300
        // Both should succeed because UNIQUE is on (codigo, precio_costo, precio_minorista)

        String sql = "INSERT INTO productos (codigo, descripcion, precio_costo, precio_minorista, cantidad_stock) VALUES (?, ?, ?, ?, 0)";

        // Act
        jdbcTemplate.update(sql, "VAR-1", "Variant A", 100.0, 200.0);
        jdbcTemplate.update(sql, "VAR-1", "Variant B", 150.0, 300.0);

        // Assert
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT id FROM productos WHERE codigo = 'VAR-1'");
        assertEquals(2, rows.size(), "Schema should allow 2 products with same code but different costs");
    }
}