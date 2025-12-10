package com.centralizesys.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public class DeudoresRepository {

    private final JdbcTemplate jdbcTemplate;

    public DeudoresRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(Long ventaId, String clienteNombre, Double montoDeuda) {
        String sql = """
            INSERT INTO deudores (venta_id, cliente_nombre, monto_deuda, fecha_deuda, estado)
            VALUES (?, ?, ?, ?, 'PENDIENTE')
        """;

        // We use Java LocalDate for the timestamp
        String fechaHoy = LocalDate.now().toString();

        jdbcTemplate.update(sql, ventaId, clienteNombre, montoDeuda, fechaHoy);
    }
}