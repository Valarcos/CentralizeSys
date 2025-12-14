package com.centralizesys.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockLocation {
    private Long id;
    private Long productoId;
    private Long locationId;
    private String locationName; // Helpful for UI ("Caja 1")
    private Integer cantidad;
}