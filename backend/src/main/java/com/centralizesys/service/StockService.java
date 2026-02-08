package com.centralizesys.service;

import com.centralizesys.model.product.StockLocation;
import com.centralizesys.repository.StockRepository;
import com.centralizesys.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StockService {

    private final StockRepository stockRepository;
    private final AuditoriaService auditoriaService;

    public StockService(StockRepository stockRepository, AuditoriaService auditoriaService) {
        this.stockRepository = stockRepository;
        this.auditoriaService = auditoriaService;
    }

    public List<StockLocation> getStockByProduct(Long productId) {
        return stockRepository.findByProductId(productId);
    }

    @Transactional
    public void addStock(Long productoId, Long ubicacionId, Long cantidad) {
        stockRepository.addStock(productoId, ubicacionId, cantidad);

        Long userId = SecurityUtils.getAuthenticatedUserId();
        auditoriaService.registrarAccion(
                userId,
                "STOCK_ADD",
                String.format("Agregado stock: +%d unidades al producto ID %d en ubicación ID %d",
                        cantidad, productoId, ubicacionId));
    }

    @Transactional
    public void subtractStock(Long productoId, Long ubicacionId, Long cantidad) {
        stockRepository.subtractStock(ubicacionId, productoId, cantidad);

        Long userId = SecurityUtils.getAuthenticatedUserId();
        auditoriaService.registrarAccion(
                userId,
                "STOCK_SUBTRACT",
                String.format("Quitado stock: -%d unidades del producto ID %d en ubicación ID %d",
                        cantidad, productoId, ubicacionId));
    }
}
