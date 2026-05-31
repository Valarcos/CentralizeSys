package com.centralizesys.model.purchase;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompraResponse {
    private Long id;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fecha;
    private String proveedor;
    private String nroComprobante;
    private Double totalCompra;
    private List<DetalleCompraResponse> items;
}