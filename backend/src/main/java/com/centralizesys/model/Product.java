package com.centralizesys.model;

public class Product {

    private Long id;
    private String codigo;
    private String descripcion;
    private Double precioCosto;
    private Double precioMayorista;
    private Double precioMinorista;

    // Read-Only field. Calculated by DB Triggers.
    // No setter provided to prevent accidental Java-side modifications.
    private Integer cantidadStock;

    // Full Constructor (Used by RowMapper)
    public Product(Long id, String codigo, String descripcion, Double precioCosto,
                   Double precioMayorista, Double precioMinorista, Integer cantidadStock) {
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
        this(null, codigo, descripcion, precioCosto, precioMayorista, precioMinorista, 0);
    }

    // Getters
    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getDescripcion() { return descripcion; }
    public Double getPrecioCosto() { return precioCosto; }
    public Double getPrecioMayorista() { return precioMayorista; }
    public Double getPrecioMinorista() { return precioMinorista; }
    public Integer getCantidadStock() { return cantidadStock; }

    // Setters (Only for editable fields)
    public void setId(Long id) { this.id = id; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public void setPrecioCosto(Double precioCosto) { this.precioCosto = precioCosto; }
    public void setPrecioMayorista(Double precioMayorista) { this.precioMayorista = precioMayorista; }
    public void setPrecioMinorista(Double precioMinorista) { this.precioMinorista = precioMinorista; }
}