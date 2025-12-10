package com.centralizesys.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetodoPago {
    private Long id;
    private String acronimo;    // E, TCM, etc.
    private String descripcion; // Efectivo, Tarjeta Macro...
}