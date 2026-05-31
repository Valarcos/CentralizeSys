package com.centralizesys.repository;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.purchase.Compra;
import com.centralizesys.model.purchase.DetalleCompra;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompraRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private CompraRepository compraRepository;

    @Test
    @DisplayName("saveCompra - inserts header and returns generated ID")
    void saveCompra_insertsAndReturnsId() {
        // Arrange
        Long userId = createTestUser();
        Compra compra = new Compra();
        compra.setFecha(LocalDateTime.now());
        compra.setProveedor("Test Supplier");
        compra.setNroComprobante("FACT-001");
        compra.setTotalCompra(1500.00);
        compra.setUsuarioId(userId);

        // Act
        long id = compraRepository.saveCompra(compra);

        // Assert
        assertThat(id).isPositive();
    }

    @Test
    @DisplayName("saveCompra - handles null userId")
    void saveCompra_handlesNullUserId() {
        // Arrange
        Compra compra = new Compra();
        compra.setFecha(LocalDateTime.now());
        compra.setProveedor("Anonymous Supplier");
        compra.setNroComprobante("FACT-002");
        compra.setTotalCompra(500.00);
        compra.setUsuarioId(null);

        // Act
        long id = compraRepository.saveCompra(compra);

        // Assert
        assertThat(id).isPositive();
    }

    @Test
    @DisplayName("saveDetalles - batch inserts details correctly")
    void saveDetalles_batchInsertsDetails() {
        // Arrange
        Long userId = createTestUser();
        Long productId = createTestProduct("COMP-001", 100.0, 0L);

        Compra compra = new Compra();
        compra.setFecha(LocalDateTime.now());
        compra.setProveedor("Batch Supplier");
        compra.setNroComprobante("FACT-003");
        compra.setTotalCompra(300.00);
        compra.setUsuarioId(userId);
        long compraId = compraRepository.saveCompra(compra);

        DetalleCompra det1 = new DetalleCompra();
        det1.setCompraId(compraId);
        det1.setProductoId(productId);
        det1.setCantidad(2L);
        det1.setCostoUnitario(50.0);
        det1.setSubtotal(100.0);

        DetalleCompra det2 = new DetalleCompra();
        det2.setCompraId(compraId);
        det2.setProductoId(productId);
        det2.setCantidad(4L);
        det2.setCostoUnitario(50.0);
        det2.setSubtotal(200.0);

        // Act
        compraRepository.saveDetalles(List.of(det1, det2));

        // Assert - Verify by counting
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM detalles_compra WHERE compra_id = ?",
                Integer.class, compraId);
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("findAll - returns all purchases with correct mapping")
    void findAll_returnsMappedCompras() {
        // Arrange
        Long userId = createTestUser();

        Compra c1 = new Compra();
        c1.setFecha(LocalDateTime.now());
        c1.setProveedor("Supplier A");
        c1.setNroComprobante("A-001");
        c1.setTotalCompra(100.00);
        c1.setUsuarioId(userId);
        compraRepository.saveCompra(c1);

        Compra c2 = new Compra();
        c2.setFecha(LocalDateTime.now());
        c2.setProveedor("Supplier B");
        c2.setNroComprobante("B-001");
        c2.setTotalCompra(200.00);
        c2.setUsuarioId(userId);
        compraRepository.saveCompra(c2);

        // Act
        List<Compra> all = compraRepository.findAll();

        // Assert
        assertThat(all).hasSize(2);
        assertThat(all).extracting(Compra::getProveedor)
                .containsExactlyInAnyOrder("Supplier A", "Supplier B");
    }

    @Test
    @DisplayName("findAll - correctly maps all fields via RowMapper")
    void findAll_mapsAllFields() {
        // Arrange
        Long userId = createTestUser();
        LocalDateTime today = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);

        Compra compra = new Compra();
        compra.setFecha(today);
        compra.setProveedor("Field Mapper Test");
        compra.setNroComprobante("MAP-001");
        compra.setTotalCompra(999.99);
        compra.setUsuarioId(userId);
        long id = compraRepository.saveCompra(compra);

        // Act
        List<Compra> results = compraRepository.findAll();
        Compra found = results.stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .orElseThrow();

        // Assert - All fields mapped correctly
        assertThat(found.getId()).isEqualTo(id);
        assertThat(found.getFecha()).isEqualTo(today);
        assertThat(found.getProveedor()).isEqualTo("Field Mapper Test");
        assertThat(found.getNroComprobante()).isEqualTo("MAP-001");
        assertThat(found.getTotalCompra()).isEqualTo(999.99);
        assertThat(found.getUsuarioId()).isEqualTo(userId);
    }
}
