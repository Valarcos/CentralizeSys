package com.centralizesys.model.audit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Auditoria {
    private Long id;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fechaHora; // ISO string from DB
    private Long usuarioId;   // Can be null (System actions or deleted users)
    private String accion;    // e.g., "LOGIN", "DELETE_PRODUCT"
    private String detalles;  // e.g., "Deleted Product ID 5 (Socks)"
}