package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.product.Product;
import com.centralizesys.model.product.StockLocation;
import com.centralizesys.model.sales.DetalleVenta;
import com.centralizesys.model.sales.Venta;
import com.centralizesys.model.sales.VentaRequest;
import com.centralizesys.model.sales.VentaResponse;
import com.centralizesys.model.sales.TipoVenta;
import com.centralizesys.repository.DeudoresRepository;
import com.centralizesys.repository.ProductRepository;
import com.centralizesys.repository.StockRepository;
import com.centralizesys.repository.VentaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VentaServiceTest {

    @Mock
    private VentaRepository ventaRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private DeudoresRepository deudoresRepository;
    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private VentaService ventaService;

    // --- GROUP 1: INPUT VALIDATION (Public API) ---

    @Test
    @DisplayName("UT-01: registrarVenta throws BusinessRuleException when items list is null")
    void registrarVenta_Throws_WhenItemsNull() {
        VentaRequest request = new VentaRequest();
        request.setItems(null);

        assertThrows(BusinessRuleException.class, () -> ventaService.registrarVenta(request));
    }

    @Test
    @DisplayName("UT-01 (Var): registrarVenta throws BusinessRuleException when items list is empty")
    void registrarVenta_Throws_WhenItemsEmpty() {
        VentaRequest request = new VentaRequest();
        request.setItems(Collections.emptyList());

        assertThrows(BusinessRuleException.class, () -> ventaService.registrarVenta(request));
    }

    @Test
    @DisplayName("UT-02: registrarVenta throws ResourceNotFoundException when product does not exist")
    void registrarVenta_Throws_WhenProductNotFound() {
        // Arrange
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(99L);
        item.setCantidad(1L);

        VentaRequest request = new VentaRequest();
        request.setItems(List.of(item));

        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> ventaService.registrarVenta(request));
    }

    // --- GROUP 2: PRICING & MATH LOGIC (Package-Private Testing) ---

    @Test
    @DisplayName("UT-03: processItems calculates discount correctly (Base - Discount)")
    void processItems_CalculatesDiscount_Correctly() {
        // Arrange
        Product product = new Product("A", "Test Product", 50.0, 80.0, 100.0);
        product.setId(1L);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(2L);
        item.setValorDescuento(10.0); // 100 - 10 = 90

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        // Act (Calling package-private method directly)
        var result = ventaService.processItems(List.of(item), TipoVenta.MINORISTA);

        // Assert
        assertEquals(180.0, result.getTotalVenta()); // 90 * 2
        DetalleVenta detail = result.getDetalles().getFirst();
        assertEquals(90.0, detail.getPrecioUnitario());
        assertEquals(10.0, detail.getDescuentoValor());
    }

    @Test
    @DisplayName("UT-16: processItems handles Zero or Null discount correctly")
    void processItems_HandlesZeroDiscount() {
        // Arrange
        Product product = new Product("A", "Test Product", 50.0, 80.0, 100.0);
        product.setId(1L);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L);
        item.setValorDescuento(null); // Should be treated as 0.0

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        // Act
        var result = ventaService.processItems(List.of(item), TipoVenta.MINORISTA);

        // Assert
        assertEquals(100.0, result.getTotalVenta());
        assertEquals(100.0, result.getDetalles().getFirst().getPrecioUnitario());
    }

    @Test
    @DisplayName("UT-04: processItems throws when discount is greater than price")
    void processItems_Throws_WhenDiscountExceedsPrice() {
        Product product = new Product("A", "Test", 50.0, 80.0, 100.0);
        product.setId(1L);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L);
        item.setValorDescuento(101.0); // Exceeds 100

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        List<VentaRequest.ItemRequest> items = List.of(item);

        assertThrows(BusinessRuleException.class, () -> ventaService.processItems(items, TipoVenta.MINORISTA));
    }

    @Test
    @DisplayName("UT-05: processItems throws when discount is negative")
    void processItems_Throws_WhenDiscountIsNegative() {
        Product product = new Product("A", "Test", 50.0, 80.0, 100.0);
        product.setId(1L);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L);
        item.setValorDescuento(-10.0);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        List<VentaRequest.ItemRequest> items = List.of(item);

        assertThrows(BusinessRuleException.class, () -> ventaService.processItems(items, TipoVenta.MINORISTA));
    }

    @Test
    @DisplayName("UT-06: processItems rounds total to two decimals")
    void processItems_RoundsTotal_ToTwoDecimals() {
        // Scenario: 3 items at 33.3333333...
        // We simulate this by having 1 product with a weird calculated price
        // OR simply 3 distinct items that sum up weirdly.
        // Let's use 1 item with quantity 1 and a calculated price that requires
        // rounding.
        // Wait, the logic is: Math.round(totalAcumulado * 100.0) / 100.0

        Product p1 = new Product("A", "P1", 10.0, 10.0, 10.555); // DB stores double
        p1.setId(1L);

        VentaRequest.ItemRequest i1 = new VentaRequest.ItemRequest();
        i1.setProductoId(1L);
        i1.setCantidad(1L);
        i1.setValorDescuento(0.0);

        when(productRepository.findById(1L)).thenReturn(Optional.of(p1));

        // Act
        var result = ventaService.processItems(List.of(i1), TipoVenta.MINORISTA);

        // Assert
        // 10.555 rounded should be 10.56
        assertEquals(10.56, result.getTotalVenta());
    }

    @Test
    @DisplayName("UT-06B: processItems uses Wholesale Price when TipoVenta is MAYORISTA")
    void processItems_UsesWholesalePrice() {
        // Arrange
        Product p = new Product("A", "P1", 50.0, 100.0, 150.0); // Cost, Wholesale, Retail
        p.setId(1L);

        VentaRequest.ItemRequest i1 = new VentaRequest.ItemRequest();
        i1.setProductoId(1L);
        i1.setCantidad(2L);

        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        // Act
        var result = ventaService.processItems(List.of(i1), TipoVenta.MAYORISTA);

        // Assert: 2 * 100.0 (Wholesale) = 200.0
        // If it used Retail, it would be 2 * 150.0 = 300.0
        assertEquals(200.0, result.getTotalVenta());
        assertEquals(100.0, result.getDetalles().getFirst().getPrecioUnitario());
    }

    // --- GLOBAL DISCOUNT TESTS ---

    @Test
    @DisplayName("UT-17: registrarVenta applies global discount correctly")
    void registrarVenta_AppliesGlobalDiscount() {
        // Arrange
        Product p = new Product("A", "P1", 50.0, 80.0, 100.0);
        p.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        // Mock stock logic to avoid NPE
        when(stockRepository.findByProductId(anyLong())).thenReturn(List.of());
        when(ventaRepository.saveVenta(any())).thenReturn(1L);
        when(ventaRepository.findVendedorNombre(any())).thenReturn("Vendedora Test");

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(2L); // Subtotal: 200
        item.setValorDescuento(0.0);

        VentaRequest request = new VentaRequest();
        request.setItems(List.of(item));
        request.setDescuentoGlobal(50.0); // 200 - 50 = 150
        request.setClienteNombre("Discount User");
        request.setUsuarioId(1L);

        // Act
        VentaResponse response = ventaService.registrarVenta(request);

        // Assert
        assertEquals(150.0, response.getTotalVenta());
        assertEquals(50.0, response.getDescuentoGlobal());
        assertEquals("Vendedora Test", response.getVendedorNombre());
    }

    @Test
    @DisplayName("UT-18: registrarVenta throws when global discount is negative")
    void registrarVenta_Throws_WhenGlobalDiscountNegative() {
        // Arrange
        Product p = new Product("A", "P1", 50.0, 80.0, 100.0);
        p.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L);

        VentaRequest request = new VentaRequest();
        request.setItems(List.of(item));
        request.setDescuentoGlobal(-10.0);

        // Act & Assert
        assertThrows(BusinessRuleException.class, () -> ventaService.registrarVenta(request));
    }

    @Test
    @DisplayName("UT-19: registrarVenta throws when global discount exceeds subtotal")
    void registrarVenta_Throws_WhenGlobalDiscountExceedsTotal() {
        // Arrange
        Product p = new Product("A", "P1", 50.0, 80.0, 100.0);
        p.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L); // Subtotal 100

        VentaRequest request = new VentaRequest();
        request.setItems(List.of(item));
        request.setDescuentoGlobal(101.0);

        // Act & Assert
        assertThrows(BusinessRuleException.class, () -> ventaService.registrarVenta(request));
    }

    // --- GROUP 3: STOCK LOGIC (Package-Private Testing) ---

    @Test
    @DisplayName("UT-07: updateStock deducts from single location with sufficient stock")
    void deductStock_DeductsFromSingleLocation() {
        // Arrange
        Long prodId = 1L;
        Long qtyNeeded = 5L;
        StockLocation loc1 = new StockLocation(1L, prodId, 100L, "Depósito", 10L); // 10 available

        when(stockRepository.findByProductId(prodId)).thenReturn(List.of(loc1));

        // Act
        String alert = ventaService.deductStockFromInventory(prodId, "Test Product", qtyNeeded);

        // Assert
        assertNull(alert); // No alert expected
        verify(stockRepository).subtractStock(100L, prodId, 5L); // 100L is locId
    }

    @Test
    @DisplayName("UT-08: updateStock deducts across multiple locations")
    void deductStock_DeductsAcrossMultipleLocations() {
        // Arrange
        Long prodId = 1L;
        Long qtyNeeded = 10L;
        // Loc1 has 4, Loc2 has 8. Total 12. Enough.
        StockLocation loc1 = new StockLocation(1L, prodId, 101L, "Loc1", 4L);
        StockLocation loc2 = new StockLocation(2L, prodId, 102L, "Loc2", 8L);

        when(stockRepository.findByProductId(prodId)).thenReturn(List.of(loc1, loc2));

        // Act
        String alert = ventaService.deductStockFromInventory(prodId, "Test Product", qtyNeeded);

        // Assert
        assertNull(alert);
        // Should take 4 from Loc1
        verify(stockRepository).subtractStock(101L, prodId, 4L);
        // Should take remaining 6 from Loc2
        verify(stockRepository).subtractStock(102L, prodId, 6L);
    }

    @Test
    @DisplayName("UT-09: updateStock forces negative when stock insufficient (Alert)")
    void deductStock_ForcesNegative_WithAlert() {
        // Arrange
        Long prodId = 1L;
        Long qtyNeeded = 10L;
        // Only 3 available
        StockLocation loc1 = new StockLocation(1L, prodId, 101L, "Loc1", 3L);

        when(stockRepository.findByProductId(prodId)).thenReturn(List.of(loc1));

        // Act
        String alert = ventaService.deductStockFromInventory(prodId, "Socks", qtyNeeded);

        // Assert
        assertNotNull(alert);
        assertTrue(alert.contains("ATENCIÓN"));
        assertTrue(alert.contains("Socks"));

        // Should take ALL 3 first
        verify(stockRepository).subtractStock(101L, prodId, 3L);
        // Then force take the remaining 7 from the SAME location (first one found)
        verify(stockRepository).subtractStock(101L, prodId, 7L);
    }

    @Test
    @DisplayName("UT-10: updateStock returns critical alert when no location exists")
    void deductStock_CriticalAlert_NoLocation() {
        // Arrange
        Long prodId = 1L;
        when(stockRepository.findByProductId(prodId)).thenReturn(Collections.emptyList());

        // Act
        String alert = ventaService.deductStockFromInventory(prodId, "Socks", 1L);

        // Assert
        assertNotNull(alert);
        assertTrue(alert.contains("CRÍTICO"));
    }

    // --- GROUP 4: DEBT & ORCHESTRATION (Public API) ---
    // Since handleDebt is private, we verify it via the Orchestrator

    @Test
    @DisplayName("UT-11: handleDebt saves debt when payment is less than total")
    void registrarVenta_SavesDebt_Correctly() {
        // Arrange
        Product p = new Product("C", "Code", 50.0, 50.0, 100.0);
        p.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(ventaRepository.saveVenta(any(Venta.class))).thenReturn(500L); // Mock generated ID

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L); // Total $100

        VentaRequest.PagoRequest pago = new VentaRequest.PagoRequest();
        pago.setMonto(80.0); // Paid $80, Debt $20
        pago.setMetodoPagoId(1L);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("John Doe");
        request.setItems(List.of(item));
        request.setPagos(List.of(pago));
        request.setUsuarioId(10L);

        // Act
        VentaResponse response = ventaService.registrarVenta(request);

        // Assert
        assertEquals(500L, response.getId());

        // Verify Debt Logic
        verify(deudoresRepository).save(500L, "John Doe", 20.0);
    }

    @Test
    @DisplayName("UT-12: handleDebt throws exception when debt exists but no client name")
    void registrarVenta_Throws_WhenDebtButNoName() {
        // Arrange
        Product p = new Product("C", "Code", 50.0, 50.0, 100.0);
        p.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(ventaRepository.saveVenta(any())).thenReturn(500L);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L); // Total 100

        // No payments -> Full Debt
        VentaRequest request = new VentaRequest();
        request.setClienteNombre(""); // BLANK NAME
        request.setItems(List.of(item));

        // Act & Assert
        assertThrows(BusinessRuleException.class, () -> ventaService.registrarVenta(request));
    }

    @Test
    @DisplayName("UT-13: handleDebt ignores micro differences (Epsilon check)")
    void registrarVenta_IgnoresMicroDebt() {
        // Arrange
        Product p = new Product("C", "Code", 50.0, 50.0, 100.0);
        p.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(ventaRepository.saveVenta(any())).thenReturn(500L);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L); // Total 100

        VentaRequest.PagoRequest pago = new VentaRequest.PagoRequest();
        pago.setMonto(99.99999); // Tiny difference < 0.0001
        pago.setMetodoPagoId(1L);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("John");
        request.setItems(List.of(item));
        request.setPagos(List.of(pago));

        // Act
        ventaService.registrarVenta(request);

        // Assert
        verify(deudoresRepository, never()).save(anyLong(), anyString(), anyDouble());
    }

    @Test
    @DisplayName("UT-14 & UT-15: Full Flow Success + Audit")
    void registrarVenta_OrchestratesFullFlow_Success() {
        // Arrange
        Product p = new Product("C", "Code", 50.0, 50.0, 100.0);
        p.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(ventaRepository.saveVenta(any())).thenReturn(500L);
        when(ventaRepository.findVendedorNombre(any())).thenReturn("Admin Test");

        // Stock Location mock to avoid NPE in loop
        when(stockRepository.findByProductId(1L)).thenReturn(
                List.of(new StockLocation(1L, 1L, 1L, "Loc", 100L)));

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(2L); // Total 200

        VentaRequest.PagoRequest pago = new VentaRequest.PagoRequest();
        pago.setMetodoPagoId(1L); // Cash, Card, etc.
        pago.setMonto(200.0); // Pay in full

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Client");
        request.setUsuarioId(7L);
        request.setItems(List.of(item));
        request.setPagos(List.of(pago)); // <--- Add this line!

        // Act
        VentaResponse response = ventaService.registrarVenta(request);

        // Assert
        assertEquals(500L, response.getId());
        assertEquals(200.0, response.getTotalVenta());
        assertEquals("Admin Test", response.getVendedorNombre());

        // Verify Interactions
        verify(ventaRepository).saveVenta(any(Venta.class));
        verify(ventaRepository).saveDetalles(anyList());
        verify(ventaRepository).savePagos(anyList()); // Empty list is fine
        verify(auditoriaService).registrarAccion(eq(7L), eq("VENTA"), contains("200.0"));
    }

    // --- GROUP 5: PAGINATION & DATE RANGE Logic ---

    @Test
    @DisplayName("UT-20: getVentasPage uses default 30-day range when dates are null")
    void getVentasPage_UsesDefaultRange_WhenNull() {
        // Arrange
        when(ventaRepository.findVentasByFechaBetween(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(ventaRepository.countVentasByFechaBetween(anyString(), anyString())).thenReturn(0L);

        // Act
        ventaService.getVentasPage(null, null, 0, 20);

        // Assert
        // Verify we called repo with dates. Since we can't easily predict "now",
        // we capture arguments or just verify it was called.
        verify(ventaRepository).findVentasByFechaBetween(anyString(), anyString(), eq(20), eq(0));
    }

    @Test
    @DisplayName("UT-21: getVentasPage throws when range exceeds 60 days")
    void getVentasPage_Throws_WhenRangeExceeds60Days() {
        // Arrange
        String start = java.time.LocalDate.now().minusDays(61).toString();
        String end = java.time.LocalDate.now().toString();

        // Act & Assert
        assertThrows(BusinessRuleException.class,
                () -> ventaService.getVentasPage(start, end, 0, 20));
    }

    @Test
    @DisplayName("UT-22: getVentasPage throws when start date is after end date")
    void getVentasPage_Throws_WhenStartAfterEnd() {
        // Arrange
        String start = java.time.LocalDate.now().toString();
        String end = java.time.LocalDate.now().minusDays(1).toString();

        // Act & Assert
        assertThrows(BusinessRuleException.class,
                () -> ventaService.getVentasPage(start, end, 0, 20));
    }
}