package com.centralizesys.repository;

import com.centralizesys.model.sales.MetodoPago;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MetodoPagoRepository {

    private final JdbcTemplate jdbcTemplate;

    public MetodoPagoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<MetodoPago> rowMapper = (rs, rowNum) -> new MetodoPago(
            rs.getLong("id"),
            rs.getString("acronimo"),
            rs.getString("descripcion")
    );

    // Used to populate the "Payment Method" dropdown in the frontend.
    public List<MetodoPago> findAll() {
        return jdbcTemplate.query("SELECT * FROM metodos_pago ORDER BY id", rowMapper);
    }
}