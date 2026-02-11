package com.centralizesys.model.debt;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeudaResponse {
    private Long id;
    private Long ventaId;
    private String clienteNombre;
    private Double montoDeuda; // The current remaining balance
    private String fechaDeuda; // dd-mm-YYYY
    private String estado; // PENDIENTE, PARCIAL, PAGADO
    private Double montoOriginal; // NEW: From ventas.total_venta
    private String fechaUltimoPago; // NEW: From deudores.fecha_pago
}