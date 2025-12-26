package com.centralizesys.model.purchase;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Compra {
    private Long id;
    private String fecha; // ISO-8601
    private String proveedor;
    private String nroComprobante;
    private Double totalCompra;
    private Long usuarioId; // Optional, for audit/ownership, company owners buying
}