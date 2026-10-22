package com.centralizesys.repository;

import com.centralizesys.model.dto.external.CatalogProductDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ExternalCatalogRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<CatalogProductDto> rowMapper = (rs, rowNum) -> new CatalogProductDto(
            rs.getString("codigo"),
            rs.getString("descripcion"),
            rs.getBoolean("disponible")
    );

    public ExternalCatalogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Retrieves all active products consolidated by family with sanitized boolean availability.
     *
     * <p>Option A (Net Family Sum):
     * Sums physical stock across variants sharing the same code. If the net sum is positive (> 0),
     * {@code disponible} is true. If zero or negative, {@code disponible} is false.
     *
     * <p>Generic Products:
     * Code '1' is partitioned by (codigo, descripcion) to preserve separate generic items.
     */
    public List<CatalogProductDto> fetchActiveCatalog() {
        // // TODO: Revisit generic products (codigo = '1') inclusion and grouping logic with the client.
        // Currently, generic products share codigo = '1' and are partitioned by (codigo, descripcion),
        // whereas standard products (codigo != '1') are consolidated solely by codigo.
        String sql = """
                SELECT 
                    codigo,
                    MIN(descripcion) AS descripcion,
                    CASE WHEN COALESCE(SUM(cantidad_stock), 0) > 0 THEN TRUE ELSE FALSE END AS disponible
                FROM productos
                WHERE activo = true
                GROUP BY 
                    codigo,
                    CASE WHEN codigo = '1' THEN descripcion ELSE '' END
                ORDER BY codigo ASC
                """;
        return jdbcTemplate.query(sql, rowMapper);
    }
}
