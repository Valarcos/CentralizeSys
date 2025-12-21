package com.centralizesys.repository;

import com.centralizesys.model.product.Product;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class ProductRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Mapeo exacto de Columnas DB (Español) -> Objeto Java
    private final RowMapper<Product> rowMapper = (rs, rowNum) ->
            new Product(
                    rs.getLong("id"),
                    rs.getString("codigo"),
                    rs.getString("descripcion"),
                    rs.getDouble("precio_costo"),
                    rs.getObject("precio_mayorista", Double.class), // Handle Nullable
                    rs.getDouble("precio_minorista"),
                    rs.getLong("cantidad_stock")
            );

    public List<Product> findAll() {
        String sql = "SELECT * FROM productos";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public Optional<Product> findById(Long id) {
        String sql = "SELECT * FROM productos WHERE id = ?";
        List<Product> results = jdbcTemplate.query(sql, rowMapper, id);
        return results.stream().findFirst();
    }

    // Buscador por Código ART (Fundamental para sync con Excel)
    public Optional<Product> findByCodigo(String codigo) {
        String sql = "SELECT * FROM productos WHERE codigo = ?";
        List<Product> results = jdbcTemplate.query(sql, rowMapper, codigo);
        return results.stream().findFirst();
    }

    // Buscador "Smart": Busca coincidencias en Código O Descripción
    // Útil para la UI "Super Intuitiva" donde el usuario escribe en un solo campo
    public List<Product> search(String query) {
        if (query == null) return List.of();
        String term = "%" + query.trim() + "%";
        String sql = "SELECT * FROM productos WHERE codigo LIKE ? OR descripcion LIKE ?";
        return jdbcTemplate.query(sql, rowMapper, term, term);
    }

    public Product save(Product producto) {
        if (producto.getId() == null) {
            return insert(producto);
        } else {
            return update(producto);
        }
    }

    private Product insert(Product p) {
        // NOTA: No insertamos cantidad_stock explícitamente, dejamos el DEFAULT 0
        String sql = """
            INSERT INTO productos (codigo, descripcion, precio_costo, precio_mayorista, precio_minorista)
            VALUES (?, ?, ?, ?, ?)
        """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, p.getCodigo());
            ps.setString(2, p.getDescripcion());
            ps.setDouble(3, p.getPrecioCosto());
            // Manejo de nulos para Double
            if (p.getPrecioMayorista() != null) ps.setDouble(4, p.getPrecioMayorista());
            else ps.setNull(4, java.sql.Types.REAL);

            ps.setDouble(5, p.getPrecioMinorista());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key != null) {
            p.setId(key.longValue());
        }
        return p;
    }

    private Product update(Product p) {
        // NOTA: NO actualizamos cantidad_stock aquí.
        // El stock se modifica SOLO moviendo items en stock_por_ubicacion (Triggers).

        // TODO: this update should be monitored. The code shouldn't be updated, but based on the case of low quality
        // products with NO inherent code, it may be required.
        String sql = """
            UPDATE productos
            SET codigo = ?, descripcion = ?, precio_costo = ?,
                precio_mayorista = ?, precio_minorista = ?
            WHERE id = ?
        """;

        jdbcTemplate.update(sql,
                p.getCodigo(),
                p.getDescripcion(),
                p.getPrecioCosto(),
                p.getPrecioMayorista(),
                p.getPrecioMinorista(),
                p.getId()
        );
        return p;
    }

    public void deleteById(Long id) {
        // El DELETE CASCADE en la DB se encargará de borrar el stock asociado
        String sql = "DELETE FROM productos WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }
}