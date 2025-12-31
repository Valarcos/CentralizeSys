package com.centralizesys.model.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {
    private Long id;
    private String nombre;
    private String email;
    private String passwordHash; // Stores BCrypt hash, NOT plain text
    private String fechaCreacion;
}