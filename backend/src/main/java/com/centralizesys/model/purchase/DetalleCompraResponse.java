package com.centralizesys.model.purchase;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetalleCompraResponse {
    private String productoCodigo;
    private String productoDescripcion;
    private Long cantidad;
    private Double costoUnitario;
    private Double subtotal;
}