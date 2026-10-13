package com.centralizesys.model.sales;

import com.centralizesys.model.cheque.AlertaCheque;
import com.centralizesys.model.debt.DeudaResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Atomic, server-computed snapshot of a sale used to render receipts.
 * Replaces several parallel client GETs that could return inconsistent (stale) state.
 * {@code deuda} is null when the sale generated no debt.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VentaReceiptDto {
    private VentaResponse venta;
    private List<DetalleVenta> items;
    private List<PagoVenta> pagos;
    private List<AlertaCheque> cheques;
    private DeudaResponse deuda;
    private Double subtotalItems;
    private Double totalDescuentos;
    private Double totalRecargos;
    private Double totalVenta;
    private Double totalPagado;
    private Double saldoRestante;
}
