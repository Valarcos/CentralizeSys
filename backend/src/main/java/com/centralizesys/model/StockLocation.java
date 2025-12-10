package com.centralizesys.model;

public class StockLocation {
    private Long id;
    private Long productoId;
    private Long locationId;
    private String locationName; // Helpful for UI ("Caja 1")
    private Integer cantidad;

    public StockLocation(Long id, Long productoId, Long locationId, String locationName, Integer cantidad) {
        this.id = id;
        this.productoId = productoId;
        this.locationId = locationId;
        this.locationName = locationName;
        this.cantidad = cantidad;
    }

    // Getters and Setters...
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProductoId() { return productoId; }
    public void setProductoId(Long productoId) { this.productoId = productoId; }

    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }

    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }

    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
}