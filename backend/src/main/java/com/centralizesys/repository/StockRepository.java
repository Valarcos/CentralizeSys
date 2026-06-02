package com.centralizesys.repository;

import com.centralizesys.exception.InfrastructureException;
import com.centralizesys.model.product.Location;
import com.centralizesys.model.product.StockLocation;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class StockRepository {

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    // Constants to satisfy Sonar
    public static final String UBICACION_ID = "ubicacionId";
    public static final String PRODUCTO_ID = "productoId";
    public static final String CANTIDAD = "cantidad";
    public static final String NOMBRE = "nombre";

    public StockRepository(NamedParameterJdbcTemplate namedJdbcTemplate) {
        this.namedJdbcTemplate = namedJdbcTemplate;
    }

    // Mapper for the Join Table (Product + Location + Qty)
    private final RowMapper<StockLocation> stockMapper = (rs, rowNum) -> new StockLocation(
            rs.getLong("id"),
            rs.getLong("producto_id"),
            rs.getLong("ubicacion_id"),
            rs.getString("nombre_ubicacion"),
            rs.getLong(CANTIDAD)
    );

    // [NEW] Mapper for pure Location (ID + Name)
    private final RowMapper<Location> locationMapper = (rs, rowNum) -> new Location(
            rs.getLong("id"),
            rs.getString(NOMBRE)
    );

    // Get all locations where a specific product is stored
    public List<StockLocation> findByProductId(Long productId) {
        String sql = """
            SELECT s.id, s.producto_id, s.ubicacion_id, u.nombre as nombre_ubicacion, s.cantidad
            FROM stock_por_ubicacion s
            JOIN ubicaciones u ON s.ubicacion_id = u.id
            WHERE s.producto_id = :productoId
        """;
        return namedJdbcTemplate.query(sql, new MapSqlParameterSource(PRODUCTO_ID, productId), stockMapper);
    }

    // Update stock in a specific box
    // Trigger will automatically update the Product Total
    public void updateQuantity(Long productId, Long ubicacionId, Long newQuantity) {
        // Upsert logic (Insert if not exists, Update if exists)
        // SQLite supports 'INSERT OR REPLACE' but 'ON CONFLICT' is better for preserving IDs
        String sql = """
            INSERT INTO stock_por_ubicacion (producto_id, ubicacion_id, cantidad)
            VALUES (:productoId, :ubicacionId, :cantidad)
            ON CONFLICT(producto_id, ubicacion_id)
            DO UPDATE SET cantidad = excluded.cantidad
        """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PRODUCTO_ID, productId)
                .addValue(UBICACION_ID, ubicacionId)
                .addValue(CANTIDAD, newQuantity);
        namedJdbcTemplate.update(sql, params);
    }

    /**
     * Adds stock to a specific location (INCREMENTAL).
     * Logic from File 1:
     * If the record exists, it ADDS to the current quantity.
     * If it doesn't exist, it creates it with the given quantity.
     */
    public void addStock(Long productoId, Long ubicacionId, Long quantityToAdd) {
        String sql = """
            INSERT INTO stock_por_ubicacion (producto_id, ubicacion_id, cantidad)
            VALUES (:productoId, :ubicacionId, :cantidad)
            ON CONFLICT(producto_id, ubicacion_id)
            DO UPDATE SET cantidad = stock_por_ubicacion.cantidad + excluded.cantidad
        """;
        // 'excluded.cantidad' refers to the value we tried to insert (quantityToAdd)
        // This effectively does: new_total = current_total + quantityToAdd

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PRODUCTO_ID, productoId)
                .addValue(UBICACION_ID, ubicacionId)
                .addValue(CANTIDAD, quantityToAdd);
        namedJdbcTemplate.update(sql, params);
    }


    /**
     * ATOMIC DECREMENT.
     * Subtracts amount from the current quantity in the DB.
     * This prevents race conditions where two users overwrite each other's data.
     */
    // This method expressly allows negative stock for cases of real sales where stock wasn't properly accounted for
    public void subtractStock(Long ubicacionId, Long productId, Long amountToSubtract) {
        String sql = """
            UPDATE stock_por_ubicacion
            SET cantidad = cantidad - :cantidad
            WHERE ubicacion_id = :ubicacionId
              AND producto_id = :productoId
        """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(UBICACION_ID, ubicacionId)
                .addValue(PRODUCTO_ID, productId)
                .addValue(CANTIDAD, amountToSubtract);
        namedJdbcTemplate.update(sql, params);
    }

    // --- LOCATION MANAGEMENT METHODS ---

    // [NEW] Returns full objects (ID + Name) for the Dropdown
    public List<Location> findAllLocations() {
        String sql = "SELECT * FROM ubicaciones ORDER BY id";
        return namedJdbcTemplate.query(sql, locationMapper);
    }

    // Helper to Create a new Location (e.g. "Caja 5") dynamically
    public Long createLocation(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Location name cannot be empty");
        }

        String sql = "INSERT INTO ubicaciones (nombre) VALUES (:nombre)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbcTemplate.update(sql, new MapSqlParameterSource(NOMBRE, name), keyHolder, new String[]{"id"});

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new InfrastructureException("Database failed to return a generated ID");
        }

        return key.longValue();
    }
}