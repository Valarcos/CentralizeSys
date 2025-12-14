package com.centralizesys.repository;

import com.centralizesys.model.DeudaResponse;
import com.centralizesys.model.enums.DebtStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class DeudoresRepository {

    private final JdbcTemplate jdbcTemplate;

    public DeudoresRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<DeudaResponse> rowMapper = (rs, rowNum) -> new DeudaResponse(
            rs.getLong("id"),
            rs.getLong("venta_id"),
            rs.getString("cliente_nombre"),
            rs.getDouble("monto_deuda"),
            rs.getString("fecha_deuda"), // SQLite stores YYYY-MM-DD
            rs.getString("estado")
    );

    public void save(Long ventaId, String clienteNombre, Double montoDeuda) {
        // Use native ISO format for SQLite compatibility
        String fechaHoy = LocalDate.now().toString();

        String sql = """
            INSERT INTO deudores (venta_id, cliente_nombre, monto_deuda, fecha_deuda, estado)
            VALUES (?, ?, ?, ?, ?)
        """;
        jdbcTemplate.update(sql, ventaId, clienteNombre, montoDeuda, fechaHoy, DebtStatus.PENDIENTE.name());
    }

    public List<DeudaResponse> findAll() {
        return jdbcTemplate.query("SELECT * FROM deudores ORDER BY id DESC", rowMapper);
    }

    // --- OPTIONAL USAGE EXPLANATION ---
    // Returns Optional<DeudaResponse> because a query by ID might result in 0 rows.
    // This forces the Service layer to explicitly handle the "Not Found" scenario.
    public Optional<DeudaResponse> findById(Long id) {
        String sql = "SELECT * FROM deudores WHERE id = ?";
        List<DeudaResponse> list = jdbcTemplate.query(sql, rowMapper, id);
        return list.stream().findFirst();
    }

    public void updateMontoAndEstado(Long id, Double nuevoMonto, String nuevoEstado) {
        // We update the Payment Date to NOW whenever the state changes/payment is made
        // Logic: If paying, we update the "last activity" date?
        // Or strictly keep "fecha_deuda" as creation?
        // Usually, we update 'fecha_pago' if state becomes PAGADO.

        String sql = "UPDATE deudores SET monto_deuda = ?, estado = ? WHERE id = ?";
        jdbcTemplate.update(sql, nuevoMonto, nuevoEstado, id);
    }
}