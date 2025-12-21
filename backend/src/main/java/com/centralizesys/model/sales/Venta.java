package com.centralizesys.model.sales;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Venta {
    private Long id;
    private String fecha;        // YYYY-MM-DD
    private String clienteNombre;
    private Double totalVenta;
    private Long usuarioId;      // For audit/security
}
