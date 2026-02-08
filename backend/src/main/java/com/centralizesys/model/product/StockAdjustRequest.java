package com.centralizesys.model.product;

/**
 * Request DTO for stock adjustment operations.
 * Used by POST /api/stock/add and POST /api/stock/subtract.
 */
public record StockAdjustRequest(
        Long productoId,
        Long ubicacionId,
        Long cantidad) {
}
