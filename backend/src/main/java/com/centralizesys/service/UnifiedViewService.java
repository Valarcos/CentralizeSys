package com.centralizesys.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class UnifiedViewService {

    private final JdbcTemplate jdbcTemplate;

    public UnifiedViewService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> getCobrosYPedidos() {
        String sql = """
            SELECT
                'FIADO' as tipo,
                d.id as id_referencia,
                d.venta_id,
                d.cliente_nombre,
                v.fecha as fecha_creacion,
                v.total_venta as monto_total,
                (v.total_venta - d.monto_deuda) as monto_pagado,
                d.monto_deuda as saldo_restante,
                d.estado
            FROM deudores d
            JOIN ventas v ON d.venta_id = v.id
            WHERE d.estado IN ('PENDIENTE', 'PARCIAL')
        
            UNION ALL
        
            SELECT
                'PEDIDO' as tipo,
                p.id as id_referencia,
                NULL as venta_id,
                p.cliente_nombre,
                p.fecha as fecha_creacion,
                p.total_estimado as monto_total,
                COALESCE((SELECT SUM(monto) FROM pagos_venta_pendiente WHERE venta_pendiente_id = p.id AND anulado = FALSE), 0) as monto_pagado,
                p.total_estimado - COALESCE((SELECT SUM(monto) FROM pagos_venta_pendiente WHERE venta_pendiente_id = p.id AND anulado = FALSE), 0) as saldo_restante,
                p.estado
            FROM ventas_pendientes p
            WHERE p.estado = 'PENDIENTE'
        
            ORDER BY fecha_creacion DESC
        """;

        return jdbcTemplate.queryForList(sql);
    }
}
