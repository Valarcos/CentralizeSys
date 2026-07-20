package com.centralizesys.model.gastos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class GastoCajaRequest {

    @NotNull(message = "El monto es obligatorio")
    @Positive(message = "El monto debe ser positivo")
    private Double monto;

    @NotBlank(message = "El motivo es obligatorio")
    private String motivo;

    // Si viene null, se usará LocalDateTime.now()
    private LocalDateTime fechaGasto;

    // Si viene null o vacío, se usará el nombre del usuario logueado
    private String personaInvolucrada;

    private String categoria;
}
