package com.centralizesys.model;

public class ProductRequest {
    private String codigo;
    private String descripcion;
    private Double precioCosto;
    private Double precioMayorista;
    private Double precioMinorista;

    // Default Constructor for JSON
    public ProductRequest() {}

    // Getters
    public String getCodigo() { return codigo; }
    public String getDescripcion() { return descripcion; }
    public Double getPrecioCosto() { return precioCosto; }
    public Double getPrecioMayorista() { return precioMayorista; }
    public Double getPrecioMinorista() { return precioMinorista; }
}