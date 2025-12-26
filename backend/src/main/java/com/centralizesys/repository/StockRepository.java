package com.centralizesys.repository;

import com.centralizesys.model.product.StockLocation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class StockRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public static final String LOCATION_ID = "locationId";
    public static final String PRODUCTO_ID = "productId";
    public static final String CANTIDAD = "cantidad";

    public StockRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    private final RowMapper<StockLocation> rowMapper = (rs, rowNum) -> new StockLocation(
            rs.getLong("id"),
            rs.getLong("producto_id"),
            rs.getLong("location_id"),
            rs.getString("location_name"), // Joined column
            rs.getLong(CANTIDAD)
    );

    // Get all locations where a specific product is stored
    public List<StockLocation> findByProductId(Long productId) {
        String sql = """
            SELECT s.id, s.producto_id, s.location_id, u.nombre as location_name, s.cantidad
            FROM stock_por_ubicacion s
            JOIN ubicaciones u ON s.location_id = u.id
            WHERE s.producto_id = :productId
        """;
        return namedJdbcTemplate.query(sql, new MapSqlParameterSource(PRODUCTO_ID, productId), rowMapper);
    }

    // Update stock in a specific box
    // Trigger will automatically update the Product Total
    public void updateQuantity(Long productId, Long locationId, Long newQuantity) {
        // Upsert logic (Insert if not exists, Update if exists)
        // SQLite supports 'INSERT OR REPLACE' but 'ON CONFLICT' is better for preserving IDs
        String sql = """
            INSERT INTO stock_por_ubicacion (producto_id, location_id, cantidad)
            VALUES (:productId, :locationId, :cantidad)
            ON CONFLICT(producto_id, location_id)
            DO UPDATE SET cantidad = excluded.cantidad
        """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PRODUCTO_ID, productId)
                .addValue(LOCATION_ID, locationId)
                .addValue(CANTIDAD, newQuantity);
        namedJdbcTemplate.update(sql, params);
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
            VALUES (:productId, :locationId, :cantidad)
            ON CONFLICT(producto_id, location_id)
            DO UPDATE SET cantidad = cantidad + excluded.cantidad
        """;
        // 'excluded.cantidad' refers to the value we tried to insert (quantityToAdd)
        // This effectively does: new_total = current_total + quantityToAdd

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PRODUCTO_ID, productoId)
                .addValue(LOCATION_ID, locationId)
                .addValue(CANTIDAD, quantityToAdd);
        namedJdbcTemplate.update(sql, params);
    }


    /**
     * ATOMIC DECREMENT.
     * Subtracts amount from the current quantity in the DB.
     * This prevents race conditions where two users overwrite each other's data.
     */
    public void subtractStock(Long locationId, Long productoId, Long amountToSubtract) {
        String sql = """
            UPDATE stock_por_ubicacion
            SET cantidad = cantidad - :amount
            WHERE location_id = :locationId AND producto_id = :productId
        """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(LOCATION_ID, locationId)
                .addValue(PRODUCTO_ID, productoId)
                .addValue("amount", amountToSubtract);
        namedJdbcTemplate.update(sql, params);
    }

    // Helper to Create a new Location (e.g. "Caja 5") dynamically
    public Long createLocation(String name) {
        String sql = "INSERT INTO ubicaciones (nombre) VALUES (:nombre)";
        namedJdbcTemplate.update(sql, new MapSqlParameterSource("nombre", name));
        return jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
    }

    public List<String> getAllLocationNames() {
        return jdbcTemplate.queryForList("SELECT nombre FROM ubicaciones", String.class);
    }
}