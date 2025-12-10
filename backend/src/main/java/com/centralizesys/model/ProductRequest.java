package com.centralizesys.model;

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
}