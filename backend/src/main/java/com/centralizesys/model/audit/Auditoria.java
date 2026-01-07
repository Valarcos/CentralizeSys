package com.centralizesys.model.audit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Auditoria {
    private Long id;
    private String fechaHora; // ISO string from DB
    private Long usuarioId;   // Can be null (System actions or deleted users)
    private String accion;    // e.g., "LOGIN", "DELETE_PRODUCT"
    private String detalles;  // e.g., "Deleted Product ID 5 (Socks)"
}