package com.centralizesys.model.debt;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
public class PagoDeudaRequest {
    private Double montoPago; // How much are they paying today?
    private Long metodoPagoId; // Payment method selected
    private String observaciones; // Optional notes
    private Long usuarioId; // The user recording the payment
    /**
     * If present, this payment will be routed to alertas_cheques instead of pagos_venta.
     * The $ amount is still counted toward the overpayment balance check.
     */
    private LocalDate fechaCobro;
}