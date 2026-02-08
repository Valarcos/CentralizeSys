package com.centralizesys.model.product;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ProductRequest {
    private String codigo;
    private String descripcion;
    private Double precioCosto;
    private Double precioMayorista;
    private Double precioMinorista;

    // For initial stock placement when creating a new product
    private Long ubicacionId;
    private Integer cantidad;
}