package com.centralizesys.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VentaResponse {
    private Long id;
    private String fecha;
    private String clienteNombre;
    private Double totalVenta;
    private List<DetalleVenta> items;
    private List<PagoVenta> pagos;

    // NEW: List of warning messages (e.g., "Stock negativo para ART-123")
    private List<String> alertas;
}