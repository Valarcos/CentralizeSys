package com.centralizesys.model;

public class ProductResponse {
    private Long id;
    private String codigo;
    private String descripcion;
    private Double precioCosto;
    private Double precioMayorista;
    private Double precioMinorista;
    private Integer cantidadStock;

    public ProductResponse(Product product) {
        this.id = product.getId();
        this.codigo = product.getCodigo();
        this.descripcion = product.getDescripcion();
        this.precioCosto = product.getPrecioCosto();
        this.precioMayorista = product.getPrecioMayorista();
        this.precioMinorista = product.getPrecioMinorista();
        this.cantidadStock = product.getCantidadStock();
    }

    // Getters
    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getDescripcion() { return descripcion; }
    public Double getPrecioCosto() { return precioCosto; }
    public Double getPrecioMayorista() { return precioMayorista; }
    public Double getPrecioMinorista() { return precioMinorista; }
    public Integer getCantidadStock() { return cantidadStock; }
}