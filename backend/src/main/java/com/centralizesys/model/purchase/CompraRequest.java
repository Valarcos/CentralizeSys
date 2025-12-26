package com.centralizesys.model.purchase;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
public class CompraRequest {
    private String proveedor;
    private String observaciones;
    private String nroComprobante;
    private Long usuarioId; // In a real app, this comes from the SecurityContext, not the JSON
    private List<CompraItemRequest> items;
}