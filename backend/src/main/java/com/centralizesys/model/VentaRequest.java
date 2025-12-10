package com.centralizesys.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class VentaRequest {
    private String clienteNombre;

    // The list of products being bought
    private List<ItemRequest> items;

    // The list of payment methods used (e.g. $500 Cash + $200 Card)
    private List<PagoRequest> pagos;

    // Nested static classes for the inner lists
    @Data
    @NoArgsConstructor
    public static class ItemRequest {
        private Long productoId;
        private Integer cantidad;

        // Frontend sends the agreed price (allows discounts/manual overrides)
        private Double precioUnitario;
    }

    @Data
    @NoArgsConstructor
    public static class PagoRequest {
        private Long metodoPagoId; // ID from metodos_pago table
        private Double monto;
    }
}