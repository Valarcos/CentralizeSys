package com.centralizesys.model.purchase;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
public class CompraRequest {
    private String proveedor;
    private String observaciones;
    private String nroComprobante;
    private Long usuarioId; // In a real app, this comes from the SecurityContext, not the JSON
    private List<CompraItemRequest> items;

    /*
     * TODO: Phase 3 - Multiple Payments for Purchases (SUSPENDED)
     * Pending client UI definitions for how to split purchase payments.
     * Do NOT uncomment until frontend is ready to send this payload natively.
     *
     * private List<PagoCompraRequest> pagos;
     *
     * @Data
     * @NoArgsConstructor
     * public static class PagoCompraRequest {
     *     private Long metodoPagoId;
     *     private Double monto;
     * }
     */
}
