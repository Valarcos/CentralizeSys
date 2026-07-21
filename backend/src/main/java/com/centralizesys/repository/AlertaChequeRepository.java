package com.centralizesys.repository;

import com.centralizesys.model.cheque.AlertaCheque;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class AlertaChequeRepository {

    private final JdbcTemplate jdbcTemplate;

    public AlertaChequeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<AlertaCheque> rowMapper = (rs, rowNum) -> new AlertaCheque(
            rs.getLong("id"),
            rs.getLong("venta_id"),
            rs.getDouble("monto"),
            rs.getDate("fecha_cobro").toLocalDate(),
            rs.getString("estado"),
            rs.getObject("pago_venta_id") != null ? rs.getLong("pago_venta_id") : null
    );

    public Long save(AlertaCheque alerta) {
        String sql = "INSERT INTO alertas_cheques (venta_id, monto, fecha_cobro, estado, pago_venta_id) VALUES (?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, alerta.getVentaId());
            ps.setDouble(2, alerta.getMonto());
            ps.setDate(3, Date.valueOf(alerta.getFechaCobro()));
            ps.setString(4, alerta.getEstado());
            if (alerta.getPagoVentaId() != null) {
                ps.setLong(5, alerta.getPagoVentaId());
            } else {
                ps.setNull(5, java.sql.Types.INTEGER);
            }
            return ps;
        }, keyHolder);

        if (keyHolder.getKeys() != null) {
            return ((Number) keyHolder.getKeys().get("id")).longValue();
        }
        return null;
    }

    public List<AlertaCheque> findByVentaId(Long ventaId) {
        String sql = "SELECT * FROM alertas_cheques WHERE venta_id = ? ORDER BY fecha_cobro ASC";
        return jdbcTemplate.query(sql, rowMapper, ventaId);
    }

    public void updateEstadoByVentaId(Long ventaId, String nuevoEstado) {
        String sql = "UPDATE alertas_cheques SET estado = ? WHERE venta_id = ?";
        jdbcTemplate.update(sql, nuevoEstado, ventaId);
    }

    public List<AlertaCheque> findPendingExpiredOrToday() {
        String sql = "SELECT * FROM alertas_cheques WHERE estado = 'PENDIENTE' AND fecha_cobro <= CURRENT_DATE ORDER BY fecha_cobro ASC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public Optional<AlertaCheque> findById(Long id) {
        String sql = "SELECT * FROM alertas_cheques WHERE id = ?";
        List<AlertaCheque> results = jdbcTemplate.query(sql, rowMapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public void updateEstadoAndPagoVentaId(Long id, String nuevoEstado, Long pagoVentaId) {
        String sql = "UPDATE alertas_cheques SET estado = ?, pago_venta_id = ? WHERE id = ?";
        jdbcTemplate.update(sql, nuevoEstado, pagoVentaId, id);
    }
}
