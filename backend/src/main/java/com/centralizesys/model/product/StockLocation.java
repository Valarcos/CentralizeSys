package com.centralizesys.model.product;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockLocation {
    private Long id;
    private Long productoId;
    private Long ubicacionId;      // Was locationId
    private String nombreUbicacion; // Was locationName
    private Long cantidad;
}