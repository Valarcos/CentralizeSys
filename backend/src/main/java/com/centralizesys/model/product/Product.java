package com.centralizesys.model.product;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Product {

    private Long id;
    private String codigo;
    private String descripcion;
    private Double precioCosto;
    private Double precioMayorista;
    private Double precioMinorista;

    // Read-Only field. Logic handled by DB Triggers.
    // Lombok's @Setter on the class generates setters for everything,
    // so we specifically disable it for this field.
    // No setter is provided to prevent accidental Java-side modifications.
    @Setter(lombok.AccessLevel.NONE)
    private Long cantidadStock;

    // Full Constructor (Used by RowMapper)
    public Product(Long id, String codigo, String descripcion, Double precioCosto,
                   Double precioMayorista, Double precioMinorista, Long cantidadStock) {
        this.id = id;
        this.codigo = codigo;
        this.descripcion = descripcion;
        this.precioCosto = precioCosto;
        this.precioMayorista = precioMayorista;
        this.precioMinorista = precioMinorista;
        this.cantidadStock = cantidadStock;
    }

    // Constructor for New Products (Stock is 0 until Locations are added)
    public Product(String codigo, String descripcion, Double precioCosto,
                   Double precioMayorista, Double precioMinorista) {
        this(null, codigo, descripcion, precioCosto, precioMayorista, precioMinorista, 0L);
    }
}