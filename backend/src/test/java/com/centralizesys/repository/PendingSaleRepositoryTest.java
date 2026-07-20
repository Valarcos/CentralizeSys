package com.centralizesys.repository;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.sales.DetalleVenta;
import com.centralizesys.model.sales.Venta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PendingSaleRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private PendingSaleRepository pendingSaleRepository;

    @Test
    @DisplayName("findById - maps cantidad_productos correctly when no items exist")
    void findById_withZeroItems_returnsZeroQuantity() {
        // Arrange
        Long userId = createTestUser();
        Venta venta = new Venta();
        venta.setFecha(LocalDateTime.of(2026, Month.JANUARY, 1, 12, 0));
        venta.setClienteNombre("No Items Test");
        venta.setTotalVenta(0.0);
        venta.setDescuentoGlobal(0.0);
        venta.setTipoVenta("M");
        venta.setUsuarioId(userId);
        venta.setEstado("PENDIENTE");

        Long ventaId = pendingSaleRepository.savePendiente(venta);

        // Act
        Optional<Venta> found = pendingSaleRepository.findById(ventaId);

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getCantidadProductos()).isZero();
    }

    @Test
    @DisplayName("findById - maps cantidad_productos correctly when items exist")
    void findById_mapsCantidadProductos() {
        // Arrange
        Long userId = createTestUser();
        Long productId = createTestProduct("PEND-001", 50.0, 5L);

        Venta venta = new Venta();
        venta.setFecha(LocalDateTime.of(2026, Month.JANUARY, 1, 12, 0));
        venta.setClienteNombre("Quantity Test");
        venta.setTotalVenta(90.0);
        venta.setDescuentoGlobal(0.0);
        venta.setTipoVenta("M");
        venta.setUsuarioId(userId);
        venta.setEstado("PENDIENTE");

        Long ventaId = pendingSaleRepository.savePendiente(venta);

        DetalleVenta det = new DetalleVenta();
        det.setVentaId(ventaId);
        det.setProductoId(productId);
        det.setCodigoSnapshot("PEND-001");
        det.setDescripcionSnapshot("Mapping Test");
        det.setCostoSnapshot(30.0);
        det.setCantidad(3L); // 3 items
        det.setPrecioLista(50.0);
        det.setDescuentoValor(5.0);
        det.setPrecioUnitario(45.0);
        det.setSubtotal(135.0);

        pendingSaleRepository.saveDetalles(List.of(det));

        // Act
        Optional<Venta> found = pendingSaleRepository.findById(ventaId);

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getCantidadProductos()).isEqualTo(3L);
    }
}
