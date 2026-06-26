package com.centralizesys.service;

import com.centralizesys.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.centralizesys.model.sales.VentaRequest;
import com.centralizesys.model.sales.VentaResponse;
import com.centralizesys.repository.VentaRepository;
import com.centralizesys.repository.StockRepository;
import com.centralizesys.model.product.StockLocation;

import java.util.List;

class VentaServiceVoidingIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private VentaService ventaService;

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private StockRepository stockRepository;

    @Test
    void shouldReturnStockToPrimaryLocationWhenSaleIsVoided() {
        // 1. Arrange: Setup Data
        Long userId = createTestUser();
        Long productId = createTestProduct("VOID-TEST", 100.0, 100L); // Price $100, Stock 100

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(productId);
        item.setCantidad(5L);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Voiding Client");
        request.setItems(List.of(item));
        request.setUsuarioId(userId);

        // Let's use Efectivo to pay fully so there's no debt.
        VentaRequest.PagoRequest pago = new VentaRequest.PagoRequest();
        pago.setMetodoPagoId(1L);
        pago.setMonto(500.0);
        request.setPagos(List.of(pago));

        // Create the sale
        VentaResponse venta = ventaService.registrarVenta(request);
        Long ventaId = venta.getId();

        // Verify stock is deducted
        Long remainingStock = getProductStock(productId);
        assertEquals(95L, remainingStock);

        // Verify state is ACTIVA
        assertEquals("ACTIVA", ventaService.getVentaById(ventaId).getEstado());

        // 2. Act: Void the sale
        ventaService.anularVentaHistorica(ventaId);

        // 3. Assert
        // State should be ANULADA
        assertEquals("ANULADA", ventaService.getVentaById(ventaId).getEstado());

        // Stock should be returned to primary location (100L)
        Long restoredStock = getProductStock(productId);
        assertEquals(100L, restoredStock);
    }

    private Long getProductStock(Long productId) {
        return stockRepository.findByProductId(productId)
                .stream()
                .mapToLong(StockLocation::getCantidad)
                .sum();
    }
}
