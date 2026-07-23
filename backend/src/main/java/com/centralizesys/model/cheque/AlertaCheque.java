package com.centralizesys.model.cheque;

import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertaCheque {
    private Long id;
    private Long ventaId;
    private Double monto;
    private LocalDate fechaCobro;
    private String estado;
    private Long pagoVentaId;

    // Transient field for UI display
    private String metodoPagoNombre;
}
