package com.centralizesys.model.cheque;

import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertaChequeRequest {
    private Double monto;
    private LocalDate fechaCobro;
}
