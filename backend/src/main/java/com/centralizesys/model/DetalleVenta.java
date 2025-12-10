package com.centralizesys.model;

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

    // Historical Snapshots
    private String codigoSnapshot;
    private String descripcionSnapshot;

    private Integer cantidad;
    private Double precioUnitario;
    private Double subtotal; // cantidad * precioUnitario
}