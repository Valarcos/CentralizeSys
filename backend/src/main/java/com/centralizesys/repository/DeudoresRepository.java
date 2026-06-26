package com.centralizesys.repository;

import com.centralizesys.model.debt.DeudaResponse;
import com.centralizesys.model.debt.PagoDeuda;
import com.centralizesys.model.enums.DebtStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class DeudoresRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private static final String MONTO = "monto";
    private static final String OBSERVACIONES = "observaciones";

    public DeudoresRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    private final RowMapper<DeudaResponse> rowMapper = (rs, rowNum) -> new DeudaResponse(
            rs.getLong("id"),
            rs.getLong("venta_id"),
            rs.getString("cliente_nombre"),
            rs.getDouble("monto_deuda"),
            rs.getObject("fecha_deuda", LocalDateTime.class),
            rs.getString("estado"),
            rs.getDouble("monto_original"),
            rs.getObject("fecha_ultimo_pago", LocalDateTime.class));

    public void save(Long ventaId, String clienteNombre, Double montoDeuda) {
        // Use native ISO format for SQLite compatibility

        String sql = """
                    INSERT INTO deudores (venta_id, cliente_nombre, monto_deuda, fecha_deuda, estado)
                    VALUES (:ventaId, :clienteNombre, :montoDeuda, :fechaDeuda, :estado)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ventaId", ventaId)
                .addValue("clienteNombre", clienteNombre)
                .addValue("montoDeuda", montoDeuda)
                .addValue("fechaDeuda", LocalDateTime.now(java.time.ZoneId.systemDefault()))
                .addValue("estado", DebtStatus.PENDIENTE.name());
        namedJdbcTemplate.update(sql, params);
    }

    public List<DeudaResponse> findAll() {
        String sql = """
                    SELECT d.*, v.total_venta as monto_original, d.fecha_pago as fecha_ultimo_pago
                    FROM deudores d
                    JOIN ventas v ON d.venta_id = v.id
                    ORDER BY d.id DESC
                """;
        return jdbcTemplate.query(sql, rowMapper);
    }

    // --- OPTIONAL USAGE EXPLANATION ---
    // Returns Optional<DeudaResponse> because a query by ID might result in 0 rows.
    // This forces the Service layer to explicitly handle the "Not Found" scenario.
    public Optional<DeudaResponse> findById(Long id) {
        String sql = """
                    SELECT d.*, v.total_venta as monto_original, d.fecha_pago as fecha_ultimo_pago
                    FROM deudores d
                    JOIN ventas v ON d.venta_id = v.id
                    WHERE d.id = :id
                """;
        List<DeudaResponse> list = namedJdbcTemplate.query(sql, new MapSqlParameterSource("id", id), rowMapper);
        return list.stream().findFirst();
    }

    public Optional<DeudaResponse> findByVentaId(Long ventaId) {
        String sql = """
                    SELECT d.*, v.total_venta as monto_original, d.fecha_pago as fecha_ultimo_pago
                    FROM deudores d
                    JOIN ventas v ON d.venta_id = v.id
                    WHERE d.venta_id = :ventaId
                """;
        List<DeudaResponse> list = namedJdbcTemplate.query(sql, new MapSqlParameterSource("ventaId", ventaId), rowMapper);
        return list.stream().findFirst();
    }

    public void updateMontoAndEstado(Long id, Double nuevoMonto, String nuevoEstado) {
        // We update the Payment Date to NOW whenever the state changes/payment is made
        String sql = "UPDATE deudores SET monto_deuda = :nuevoMonto, estado = :nuevoEstado WHERE id = :id";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("nuevoMonto", nuevoMonto)
                .addValue("nuevoEstado", nuevoEstado)
                .addValue("id", id);
        namedJdbcTemplate.update(sql, params);
    }

    /**
     * Check if there are any active (non-PAGADO, non-ANULADA) debts OR any open pending sales.
     * Used by the frontend dashboard reminder badge, which covers both the deudores
     * and ventas_pendientes sections of the CobrosYPedidos page.
     */
    public boolean hasActiveDebts() {
        String sql = """
                    SELECT (
                        EXISTS (SELECT 1 FROM deudores WHERE estado IN ('PENDIENTE', 'PARCIAL'))
                        OR
                        EXISTS (SELECT 1 FROM ventas_pendientes WHERE estado = 'PENDIENTE')
                    )
                """;
        Boolean result = jdbcTemplate.queryForObject(sql, Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    public List<DeudaResponse> findExpiredDebts(int days) {
        // SQLite syntax: date('now', '-X days')
        String sql = """
                    SELECT d.*, v.total_venta as monto_original, d.fecha_pago as fecha_ultimo_pago
                    FROM deudores d
                    JOIN ventas v ON d.venta_id = v.id
                    WHERE d.estado IN ('PENDIENTE', 'PARCIAL')
                    AND d.fecha_deuda <= :thresholdDate
                    ORDER BY d.fecha_deuda ASC
                """;

        return namedJdbcTemplate.query(sql,
                new MapSqlParameterSource("thresholdDate", LocalDateTime.now(java.time.ZoneId.systemDefault()).minusDays(days)),
                rowMapper);
    }

    public List<PagoDeuda> getPagosByDeudaId(Long deudaId) {
        String sql = """
                    SELECT p.*, m.descripcion as metodo_pago_nombre, u.nombre as usuario_nombre
                    FROM pagos_deuda p
                    JOIN metodos_pago m ON p.metodo_pago_id = m.id
                    LEFT JOIN usuarios u ON p.usuario_id = u.id
                    WHERE p.deuda_id = :deudaId
                    ORDER BY p.fecha_pago DESC, p.id DESC
                """;

        return namedJdbcTemplate.query(sql,
                new MapSqlParameterSource("deudaId", deudaId),
                (rs, rowNum) -> new PagoDeuda(
                        rs.getLong("id"),
                        rs.getLong("deuda_id"),
                        rs.getLong("metodo_pago_id"),
                        rs.getDouble(MONTO),
                        rs.getObject("fecha_pago", LocalDateTime.class),
                        rs.getString(OBSERVACIONES),
                        rs.getLong("usuario_id"),
                        rs.getBoolean("anulado"),
                        rs.getString("metodo_pago_nombre"),
                        rs.getString("usuario_nombre")));
    }

    public Optional<PagoDeuda> findPagoById(Long pagoId) {
        String sql = """
                    SELECT p.*, m.descripcion as metodo_pago_nombre, u.nombre as usuario_nombre
                    FROM pagos_deuda p
                    JOIN metodos_pago m ON p.metodo_pago_id = m.id
                    LEFT JOIN usuarios u ON p.usuario_id = u.id
                    WHERE p.id = :pagoId
                """;

        List<PagoDeuda> list = namedJdbcTemplate.query(sql,
                new MapSqlParameterSource("pagoId", pagoId),
                (rs, rowNum) -> new PagoDeuda(
                        rs.getLong("id"),
                        rs.getLong("deuda_id"),
                        rs.getLong("metodo_pago_id"),
                        rs.getDouble(MONTO),
                        rs.getObject("fecha_pago", LocalDateTime.class),
                        rs.getString(OBSERVACIONES),
                        rs.getLong("usuario_id"),
                        rs.getBoolean("anulado"),
                        rs.getString("metodo_pago_nombre"),
                        rs.getString("usuario_nombre")));
        return list.stream().findFirst();
    }

    public void updatePagoAnulado(Long pagoId) {
        String sql = "UPDATE pagos_deuda SET anulado = true WHERE id = :pagoId";
        namedJdbcTemplate.update(sql, new MapSqlParameterSource("pagoId", pagoId));
    }

    public void insertarPagoDeuda(Long deudaId, Long metodoPagoId, Double monto, String observaciones, Long usuarioId) {
        String sql = """
                    INSERT INTO pagos_deuda (deuda_id, metodo_pago_id, monto, fecha_pago, observaciones, usuario_id)
                    VALUES (:deudaId, :metodoPagoId, :monto, CURRENT_TIMESTAMP, :observaciones, :usuarioId)
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("deudaId", deudaId)
                .addValue("metodoPagoId", metodoPagoId)
                .addValue(MONTO, monto)
                // fecha_pago handled by sql
                .addValue(OBSERVACIONES, observaciones)
                .addValue("usuarioId", usuarioId);

        namedJdbcTemplate.update(sql, params);
    }
}