package com.centralizesys.model.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Domain entity representing an external machine-to-machine (M2M) API Key.
 *
 * <p>Authentication is performed exclusively against the cryptographic SHA-256
 * {@code keyHash}; plain API keys are never stored in the database.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey {
    private Long id;
    private String nombre;
    private String keyHash;
    private boolean activo;
    private LocalDateTime creadoEn;
    private LocalDateTime ultimoAcceso;
    private int rateLimitRpm;
}
