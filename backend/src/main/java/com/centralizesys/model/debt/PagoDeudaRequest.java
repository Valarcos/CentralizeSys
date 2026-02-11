package com.centralizesys.model.debt;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PagoDeudaRequest {
    private Double montoPago; // How much are they paying today?
    private Long metodoPagoId; // Payment method selected
    private String observaciones; // Optional notes
    private Long usuarioId; // The user recording the payment
}