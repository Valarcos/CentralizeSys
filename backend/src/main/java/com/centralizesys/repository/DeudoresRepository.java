package com.centralizesys.repository;

import com.centralizesys.model.debt.DeudaResponse;
import com.centralizesys.model.enums.DebtStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class DeudoresRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public DeudoresRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    private final RowMapper<DeudaResponse> rowMapper = (rs, rowNum) -> new DeudaResponse(
            rs.getLong("id"),
            rs.getLong("venta_id"),
            rs.getString("cliente_nombre"),
            rs.getDouble("monto_deuda"),
            rs.getString("fecha_deuda"), // SQLite stores YYYY-MM-DD
            rs.getString("estado"));

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
                .addValue("fechaDeuda", LocalDate.now().toString())
                .addValue("estado", DebtStatus.PENDIENTE.name());
        namedJdbcTemplate.update(sql, params);
    }

    public List<DeudaResponse> findAll() {
        return jdbcTemplate.query("SELECT * FROM deudores ORDER BY id DESC", rowMapper);
    }

    // --- OPTIONAL USAGE EXPLANATION ---
    // Returns Optional<DeudaResponse> because a query by ID might result in 0 rows.
    // This forces the Service layer to explicitly handle the "Not Found" scenario.
    public Optional<DeudaResponse> findById(Long id) {
        String sql = "SELECT * FROM deudores WHERE id = :id";
        List<DeudaResponse> list = namedJdbcTemplate.query(sql, new MapSqlParameterSource("id", id), rowMapper);
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
     * Check if there are any active (non-PAGADO) debts.
     * Used by frontend for 15-day reminder badge.
     */
    public boolean hasActiveDebts() {
        String sql = "SELECT COUNT(*) FROM deudores WHERE estado != 'PAGADO'";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null && count > 0;
    }
}