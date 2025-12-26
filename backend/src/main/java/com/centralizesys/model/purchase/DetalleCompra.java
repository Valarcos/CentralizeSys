package com.centralizesys.model.purchase;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DetalleCompra {
    private Long id;
    private Long compraId;
    private Long productoId;
    private Long cantidad;
    private Double costoUnitario;  // Cost at the moment of purchase
    private Double subtotal;
}