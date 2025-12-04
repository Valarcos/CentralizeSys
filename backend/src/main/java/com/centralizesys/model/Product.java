package com.centralizesys.model;

public class Product {

    private Long id;
    private String name;
    private Integer stock;
    private Double price;

    public Product(Long id, String name, Integer stock, Double price) {
        this.id = id;
        this.name = name;
        this.stock = stock;
        this.price = price;
    }

    // Case of constructor chaining. Eliminates the need of writing null for the ID everytime one is created
    public Product(String name, Integer stock, Double price) {
        this(null, name, stock, price);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }
}