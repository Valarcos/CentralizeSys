package com.centralizesys.model.debt;

import com.centralizesys.model.cheque.AlertaCheque;
import com.centralizesys.model.sales.DetalleVenta;
import com.centralizesys.model.sales.VentaResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Atomic, server-computed snapshot of a debt used to render debtor receipts.
 * In schema V10 every debt is bound to a parent sale ({@code venta_id NOT NULL}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeudaReceiptDto {
    private DeudaResponse deuda;
    private VentaResponse venta;
    private List<DetalleVenta> items;
    private List<PagoDeuda> pagosDeuda;
    private List<AlertaCheque> cheques;
    private Double totalOriginal;
    private Double totalPagado;
    private Double saldoRestante;
}
