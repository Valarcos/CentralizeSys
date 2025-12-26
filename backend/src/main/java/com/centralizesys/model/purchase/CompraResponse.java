package com.centralizesys.model.purchase;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompraResponse {
    private Long id;
    private String fecha;
    private String proveedor;
    private String nroComprobante;
    private Double totalCompra;
    private List<DetalleCompraResponse> items;
}