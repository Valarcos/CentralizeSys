package com.centralizesys.repository;

import com.centralizesys.model.product.Product;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProductRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public ProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    // Mapeo exacto de Columnas DB (Español) -> Objeto Java
    private final RowMapper<Product> rowMapper = (rs, rowNum) -> new Product(
            rs.getLong("id"),
            rs.getString("codigo"),
            rs.getString("descripcion"),
            rs.getDouble("precio_costo"),
            rs.getObject("precio_mayorista", Double.class), // Handle Nullable
            rs.getDouble("precio_minorista"),
            rs.getLong("cantidad_stock"));

    public List<Product> findAll() {
        String sql = "SELECT * FROM productos";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public Optional<Product> findById(Long id) {
        String sql = "SELECT * FROM productos WHERE id = :id";
        List<Product> results = namedJdbcTemplate.query(sql, new MapSqlParameterSource("id", id), rowMapper);
        return results.stream().findFirst();
    }

    // Fetch multiple IDs at once to avoid N+1 problem
    public List<Product> findAllById(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        // Static SQL allows DB caching and satisfies Sonar
        String sql = "SELECT * FROM productos WHERE id IN (:ids)";
        MapSqlParameterSource parameters = new MapSqlParameterSource("ids", ids);
        return namedJdbcTemplate.query(sql, parameters, rowMapper);
    }

    // Buscador por Código ART (Fundamental para sync con Excel)
    // Uses List<Product> to support multiple variants (same code, different cost)
    public List<Product> findAllByCodigo(String codigo) {
        String sql = "SELECT * FROM productos WHERE codigo = :codigo";
        return namedJdbcTemplate.query(sql, new MapSqlParameterSource("codigo", codigo), rowMapper);
    }

    // Buscador "Smart": Busca coincidencias en Código O Descripción
    // Útil para la UI "Super Intuitiva" donde el usuario escribe en un solo campo
    public List<Product> search(String query) {
        if (query == null)
            return List.of();
        String term = "%" + query.trim() + "%";
        // STRICT: Limit to 100 to prevent UI performance issues
        String sql = "SELECT * FROM productos WHERE codigo LIKE :termino OR descripcion LIKE :termino LIMIT 100";

        return namedJdbcTemplate.query(sql, new MapSqlParameterSource("termino", term), rowMapper);
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
        // [NOTE] Params (:codigo, :descripcion, etc) match DTO fields automatically via
        // BeanPropertySqlParameterSource
        String sql = """
                    INSERT INTO productos (codigo, descripcion, precio_costo, precio_mayorista, precio_minorista)
                    VALUES (:codigo, :descripcion, :precioCosto, :precioMayorista, :precioMinorista)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        SqlParameterSource params = new BeanPropertySqlParameterSource(p);

        namedJdbcTemplate.update(sql, params, keyHolder);

        Number key = keyHolder.getKey();
        if (key != null) {
            p.setId(key.longValue());
        }
        return p;
    }

    private Product update(Product p) {
        // NOTA: NO actualizamos cantidad_stock aquí.
        // El stock se modifica SOLO moviendo items en stock_por_ubicacion (Triggers).

        // TODO: this update should be monitored. The article code shouldn't be updated, but
        //  based on the possible case of low quality products with NO inherent art code, it may be required.
        String sql = """
                    UPDATE productos
                    SET codigo = :codigo,
                        descripcion = :descripcion,
                        precio_costo = :precioCosto,
                        precio_mayorista = :precioMayorista,
                        precio_minorista = :precioMinorista
                    WHERE id = :id
                """;
        SqlParameterSource params = new BeanPropertySqlParameterSource(p);
        namedJdbcTemplate.update(sql, params);
        return p;
    }

    public void deleteById(Long id) {
        // El DELETE CASCADE en la DB se encargará de borrar el stock asociado
        String sql = "DELETE FROM productos WHERE id = :id";
        namedJdbcTemplate.update(sql, new MapSqlParameterSource("id", id));
    }
}