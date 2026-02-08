package com.centralizesys.service;

import com.centralizesys.model.product.StockLocation;
import com.centralizesys.repository.StockRepository;
import com.centralizesys.security.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private StockRepository stockRepository;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private StockService stockService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    @BeforeEach
    void setUp() {
        securityUtilsMock = mockStatic(SecurityUtils.class);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    @Test
    void getStockByProduct_ShouldReturnStockLocations() {
        Long productId = 1L;
        List<StockLocation> expectedStock = List.of(
                new StockLocation(1L, productId, 10L, "Caja 1", 5L));

        when(stockRepository.findByProductId(productId)).thenReturn(expectedStock);

        List<StockLocation> result = stockService.getStockByProduct(productId);

        assertEquals(expectedStock, result);
        verify(stockRepository).findByProductId(productId);
    }

    @Test
    void addStock_ShouldCallRepositoryAndAudit() {
        Long productId = 1L;
        Long locationId = 10L;
        Long quantity = 5L;
        Long userId = 99L;

        securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);

        stockService.addStock(productId, locationId, quantity);

        verify(stockRepository).addStock(productId, locationId, quantity);
        verify(auditoriaService).registrarAccion(
                eq(userId),
                eq("STOCK_ADD"),
                contains("Agregado stock: +5 unidades"));
    }

    @Test
    void subtractStock_ShouldCallRepositoryAndAudit() {
        Long productId = 1L;
        Long locationId = 10L;
        Long quantity = 3L;
        Long userId = 99L;

        securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);

        stockService.subtractStock(productId, locationId, quantity);

        verify(stockRepository).subtractStock(locationId, productId, quantity);
        verify(auditoriaService).registrarAccion(
                eq(userId),
                eq("STOCK_SUBTRACT"),
                contains("Quitado stock: -3 unidades"));
    }
}
