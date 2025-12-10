package com.centralizesys.model;

import lombok.Getter;

@Getter
public class ProductResponse {
    private final Long id;
    private final String codigo;
    private final String descripcion;
    private final Double precioCosto;
    private final Double precioMayorista;
    private final Double precioMinorista;
    private final Integer cantidadStock;

    public ProductResponse(Product product) {
        this.id = product.getId();
        this.codigo = product.getCodigo();
        this.descripcion = product.getDescripcion();
        this.precioCosto = product.getPrecioCosto();
        this.precioMayorista = product.getPrecioMayorista();
        this.precioMinorista = product.getPrecioMinorista();
        this.cantidadStock = product.getCantidadStock();
    }
}