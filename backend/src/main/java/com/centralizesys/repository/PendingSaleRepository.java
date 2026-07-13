package com.centralizesys.repository;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.exception.InfrastructureException;
import com.centralizesys.model.sales.DetalleVenta;
import com.centralizesys.model.sales.PagoVenta;
import com.centralizesys.model.sales.Venta;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class PendingSaleRepository {

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    // Constant prevents typos across multiple SQL strings
    private static final String VENTA_PENDIENTE_ID_PARAM = "ventaPendienteId";
    private static final String MONTO = "monto";

    public PendingSaleRepository(JdbcTemplate jdbcTemplate) {
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    private final RowMapper<Venta> pendingVentaMapper = (rs, rowNum) -> {
        Long usuarioIdVal = rs.getLong("usuario_id");
        Long usuarioId = rs.wasNull() ? null : usuarioIdVal;
        return new Venta(
                rs.getLong("id"),
                rs.getObject("fecha", LocalDateTime.class),
                rs.getString("cliente_nombre"),
                rs.getDouble("total_estimado"), // Note: table uses total_estimado
                rs.getDouble("descuento_global"),
                rs.getString("tipo_venta"),
                usuarioId,
                rs.getString("estado"),
                null);
    };

    private final RowMapper<DetalleVenta> pendingDetalleMapper = (rs, rowNum) -> new DetalleVenta(
            rs.getLong("id"),
            rs.getLong("venta_pendiente_id"),
            rs.getLong("producto_id"),
            rs.getString("codigo_snapshot"),
            rs.getString("descripcion_snapshot"),
            rs.getDouble("costo_snapshot"),
            rs.getLong("cantidad"),
            rs.getDouble("precio_lista"),
            rs.getDouble("descuento_valor"),
            rs.getDouble("precio_unitario"),
            rs.getDouble("subtotal"));

    // --- WRITE OPERATIONS ---

    public Long savePendiente(Venta venta) {
        String sql = """
                    INSERT INTO ventas_pendientes (fecha, cliente_nombre, total_estimado, descuento_global, tipo_venta, usuario_id, estado)
                    VALUES (:fecha, :clienteNombre, :totalVenta, :descuentoGlobal, :tipoVenta, :usuarioId, :estado)
                """;

        SqlParameterSource params = new BeanPropertySqlParameterSource(venta);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbcTemplate.update(sql, params, keyHolder, new String[]{"id"});

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new InfrastructureException("La base de datos no devolvió el ID de la venta pendiente guardada.");
        }
        return key.longValue();
    }

    public void saveDetalles(List<DetalleVenta> detalles) {
        String sql = """
                    INSERT INTO detalles_venta_pendiente
                    (venta_pendiente_id, producto_id, codigo_snapshot, descripcion_snapshot, costo_snapshot, cantidad,
                     precio_lista, descuento_valor, precio_unitario, subtotal)
                    VALUES (:ventaId,
                            :productoId,
                            :codigoSnapshot,
                            :descripcionSnapshot,
                            :costoSnapshot,
                            :cantidad,
                            :precioLista,
                            :descuentoValor,
                            :precioUnitario,
                            :subtotal)
                """;

        List<MapSqlParameterSource> batchParams = detalles.stream()
                .map(d -> new MapSqlParameterSource()
                        .addValue("ventaId", d.getVentaId())
                        .addValue("productoId", d.getProductoId())
                        .addValue("codigoSnapshot", d.getCodigoSnapshot())
                        .addValue("descripcionSnapshot", d.getDescripcionSnapshot())
                        .addValue("costoSnapshot", d.getCostoSnapshot())
                        .addValue("cantidad", d.getCantidad())
                        .addValue("precioLista", d.getPrecioLista())
                        .addValue("descuentoValor", d.getDescuentoValor())
                        .addValue("precioUnitario", d.getPrecioUnitario())
                        .addValue("subtotal", d.getSubtotal()))
                .toList();

        namedJdbcTemplate.batchUpdate(sql, batchParams.toArray(new MapSqlParameterSource[0]));
    }

    /**
     * Inserts a single deposit payment for a pending sale and atomically increments
     * the monto_pagado header column. Both operations happen within the same service
     * transaction, providing consistency without a separate scheduled reconciliation job.
     */
    public void savePagoVentaPendiente(Long ventaPendienteId, Long metodoPagoId, Double monto, Long usuarioId) {
        String insertSql = """
                    INSERT INTO pagos_venta_pendiente
                        (venta_pendiente_id, metodo_pago_id, monto, fecha_pago, anulado, usuario_id)
                    VALUES (:ventaPendienteId, :metodoPagoId, :monto, CURRENT_TIMESTAMP, false, :usuarioId)
                """;
        namedJdbcTemplate.update(insertSql, new MapSqlParameterSource()
                .addValue(VENTA_PENDIENTE_ID_PARAM, ventaPendienteId)
                .addValue("metodoPagoId", metodoPagoId)
                .addValue(MONTO, monto)
                .addValue("usuarioId", usuarioId));

        // Atomically update the header's accumulated total
        String updateSql = """
                    UPDATE ventas_pendientes
                    SET monto_pagado = monto_pagado + :monto
                    WHERE id = :ventaPendienteId
                """;
        namedJdbcTemplate.update(updateSql, new MapSqlParameterSource()
                .addValue(MONTO, monto)
                .addValue(VENTA_PENDIENTE_ID_PARAM, ventaPendienteId));
    }

    public void marcarDetallesComoAnulados(Long ventaPendienteId) {
        String sql = "UPDATE detalles_venta_pendiente SET anulado = true WHERE venta_pendiente_id = :id";
        namedJdbcTemplate.update(sql, new MapSqlParameterSource("id", ventaPendienteId));
    }

    public void updateTotalesConOCC(Long id, Double nuevoTotalEstimado, Double nuevoDescuentoGlobal) {
        String sql = """
                    UPDATE ventas_pendientes
                    SET total_estimado = :nuevoTotalEstimado, descuento_global = :nuevoDescuentoGlobal
                    WHERE id = :id AND estado = 'PENDIENTE'
                """;
        int rows = namedJdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("nuevoTotalEstimado", nuevoTotalEstimado)
                .addValue("nuevoDescuentoGlobal", nuevoDescuentoGlobal)
                .addValue("id", id));

        if (rows == 0) {
            throw new BusinessRuleException("No se pudo actualizar los totales (estado modificado concurrentemente).");
        }
    }

    public void updatePagoAnulado(Long pagoId) {
        String sql = "UPDATE pagos_venta_pendiente SET anulado = true WHERE id = :pagoId";
        namedJdbcTemplate.update(sql, new MapSqlParameterSource("pagoId", pagoId));
    }

    public void decrementMontoPagado(Long ventaPendienteId, Double monto) {
        String updateSql = """
                    UPDATE ventas_pendientes
                    SET monto_pagado = monto_pagado - :monto
                    WHERE id = :ventaPendienteId
                """;
        namedJdbcTemplate.update(updateSql, new MapSqlParameterSource()
                .addValue(MONTO, monto)
                .addValue(VENTA_PENDIENTE_ID_PARAM, ventaPendienteId));
    }


    /**
     * Fetches all active (non-voided) payment records for the given pending sale.
     * Used by finalizarVenta to migrate payments to the finalized Venta's pagos_venta.
     */
    public List<PagoVenta> findPagosActivosByVentaPendienteId(Long ventaPendienteId) {
        String sql = """
                    SELECT id, venta_pendiente_id AS venta_id, metodo_pago_id, monto
                    FROM pagos_venta_pendiente
                    WHERE venta_pendiente_id = :ventaPendienteId
                      AND anulado = false
                """;
        return namedJdbcTemplate.query(sql,
                new MapSqlParameterSource(VENTA_PENDIENTE_ID_PARAM, ventaPendienteId),
                (rs, rowNum) -> new PagoVenta(
                        rs.getLong("id"),
                        rs.getLong("venta_id"),
                        rs.getLong("metodo_pago_id"),
                        rs.getDouble(MONTO)));
    }

    public Double getMontoPagoActivo(Long pagoId, Long ventaPendienteId) {
        String sql = "SELECT monto FROM pagos_venta_pendiente WHERE id = :pagoId AND venta_pendiente_id = :ventaId AND anulado = false";
        try {
            return namedJdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                    .addValue("pagoId", pagoId)
                    .addValue("ventaId", ventaPendienteId), Double.class);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new BusinessRuleException("El pago no existe, no pertenece a este pedido o ya está anulado.");
        }
    }

    /**
     * Returns the sum of all active (non-voided) payments for a pending sale.
     * Used to validate balance checks without fetching the full list.
     */
    public Double sumPagosActivosByVentaPendienteId(Long ventaPendienteId) {
        String sql = """
                    SELECT COALESCE(SUM(monto), 0.0)
                    FROM pagos_venta_pendiente
                    WHERE venta_pendiente_id = :ventaPendienteId
                      AND anulado = false
                """;
        return namedJdbcTemplate.queryForObject(sql,
                new MapSqlParameterSource(VENTA_PENDIENTE_ID_PARAM, ventaPendienteId),
                Double.class);
    }

    // --- READ OPERATIONS ---

    public Optional<Venta> findById(Long id) {
        String sql = "SELECT * FROM ventas_pendientes WHERE id = :id";
        List<Venta> list = namedJdbcTemplate.query(sql, new MapSqlParameterSource("id", id), pendingVentaMapper);
        return list.stream().findFirst();
    }

    public List<DetalleVenta> findDetallesByVentaPendienteId(Long id) {
        String sql = "SELECT * FROM detalles_venta_pendiente WHERE venta_pendiente_id = :id AND (anulado = false OR anulado IS NULL)";
        return namedJdbcTemplate.query(sql, new MapSqlParameterSource("id", id), pendingDetalleMapper);
    }

    // --- UPDATE OPERATIONS ---

    public void updateEstado(Long id, String estado) {
        String sql = "UPDATE ventas_pendientes SET estado = :estado WHERE id = :id";
        namedJdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("estado", estado)
                .addValue("id", id));
    }
}
