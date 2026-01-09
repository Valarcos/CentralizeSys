package com.centralizesys.model.sales;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetalleVenta {
    private Long id;
    private Long ventaId;
    private Long productoId;

    private String codigoSnapshot;
    private String descripcionSnapshot;

    private Long cantidad;

    private Double precioLista;         // Original Price
    private Double descuentoValor;      // The input value

    private Double precioUnitario;      // Final Price
    private Double subtotal;
}