package com.centralizesys.repository;

import com.centralizesys.model.product.StockLocation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class StockRepository {

    private final JdbcTemplate jdbcTemplate;

    public StockRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<StockLocation> rowMapper = (rs, rowNum) -> new StockLocation(
            rs.getLong("id"),
            rs.getLong("producto_id"),
            rs.getLong("location_id"),
            rs.getString("location_name"), // Joined column
            rs.getLong("cantidad")
    );

    // Get all locations where a specific product is stored
    public List<StockLocation> findByProductId(Long productId) {
        String sql = """
            SELECT s.id, s.producto_id, s.location_id, u.nombre as location_name, s.cantidad
            FROM stock_por_ubicacion s
            JOIN ubicaciones u ON s.location_id = u.id
            WHERE s.producto_id = ?
        """;
        return jdbcTemplate.query(sql, rowMapper, productId);
    }

    // Update stock in a specific box
    // Trigger will automatically update the Product Total
    public void updateQuantity(Long productId, Long locationId, Long newQuantity) {
        // Upsert logic (Insert if not exists, Update if exists)
        // SQLite supports 'INSERT OR REPLACE' but 'ON CONFLICT' is better for preserving IDs
        String sql = """
            INSERT INTO stock_por_ubicacion (producto_id, location_id, cantidad)
            VALUES (?, ?, ?)
            ON CONFLICT(producto_id, location_id) 
            DO UPDATE SET cantidad = excluded.cantidad
        """;
        jdbcTemplate.update(sql, productId, locationId, newQuantity);
    }

    /**
     * Adds stock to a specific location (INCREMENTAL).
     * Logic from File 1:
     * If the record exists, it ADDS to the current quantity.
     * If it doesn't exist, it creates it with the given quantity.
     */
    public void addStock(Long productoId, Long locationId, Long quantityToAdd) {
        String sql = """
            INSERT INTO stock_por_ubicacion (producto_id, location_id, cantidad)
            VALUES (?, ?, ?)
            ON CONFLICT(producto_id, location_id)
            DO UPDATE SET cantidad = cantidad + excluded.cantidad
        """;

        // 'excluded.cantidad' refers to the value we tried to insert (quantityToAdd)
        // This effectively does: new_total = current_total + quantityToAdd
        jdbcTemplate.update(sql, productoId, locationId, quantityToAdd);
    }

    /**
     * ATOMIC DECREMENT.
     * Subtracts amount from the current quantity in the DB.
     * This prevents race conditions where two users overwrite each other's data.
     */
    public void subtractStock(Long locationId, Long productoId, Long amountToSubtract) {
        String sql = """
            UPDATE stock_por_ubicacion
            SET cantidad = cantidad - ?
            WHERE location_id = ? AND producto_id = ?
        """;
        jdbcTemplate.update(sql, amountToSubtract, locationId, productoId);
    }

    // Helper to Create a new Location (e.g. "Caja 5") dynamically
    public Long createLocation(String name) {
        String sql = "INSERT INTO ubicaciones (nombre) VALUES (?)";
        jdbcTemplate.update(sql, name);
        return jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
    }

    public List<String> getAllLocationNames() {
        return jdbcTemplate.queryForList("SELECT nombre FROM ubicaciones", String.class);
    }
}