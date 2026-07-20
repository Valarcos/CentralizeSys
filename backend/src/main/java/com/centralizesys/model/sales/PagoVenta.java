package com.centralizesys.model.sales;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagoVenta {
    private Long id;
    private Long ventaId;
    private Long metodoPagoId; // Links to 'metodos_pago' table
    private Double monto;
    private LocalDateTime fechaPago;
    private Boolean anulado;
    private Long usuarioId;
}