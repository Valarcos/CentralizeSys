package com.centralizesys.model.debt;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagoDeuda {
    private Long id;
    private Long deudaId;
    private Long metodoPagoId;
    private Double monto;
    private String fechaPago;
    private String observaciones;
    private Long usuarioId;

    // Helper field for reading human readable values from JOINs
    private String metodoPagoNombre;
    private String usuarioNombre;
}
