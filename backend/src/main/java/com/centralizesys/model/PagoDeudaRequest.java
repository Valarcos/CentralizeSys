package com.centralizesys.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PagoDeudaRequest {
    private Double montoPago; // How much are they paying today?
}