package com.centralizesys.repository;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.dto.external.CatalogProductDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalCatalogRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ExternalCatalogRepository externalCatalogRepository;

    @Test
    @DisplayName("fetchActiveCatalog collapses variants, clamps net stock, handles generic items, and excludes inactive products")
    void fetchActiveCatalog_AggregatesAndSanitizesCorrectly() {
        // Clear products table to ensure clean assertions
        jdbcTemplate.execute("DELETE FROM productos");

        // 1. Family with positive net stock (10 + (-3) = 7 > 0) -> disponible = true
        jdbcTemplate.update("""
                INSERT INTO productos (codigo, descripcion, precio_costo, precio_minorista, cantidad_stock, activo)
                VALUES ('FAM-POS', 'Remera Negra Talle S', 50.0, 100.0, 10, true)
                """);
        jdbcTemplate.update("""
                INSERT INTO productos (codigo, descripcion, precio_costo, precio_minorista, cantidad_stock, activo)
                VALUES ('FAM-POS', 'Remera Negra Talle M', 55.0, 100.0, -3, true)
                """);

        // 2. Family with negative net stock (-2 + (-5) = -7 <= 0) -> disponible = false
        jdbcTemplate.update("""
                INSERT INTO productos (codigo, descripcion, precio_costo, precio_minorista, cantidad_stock, activo)
                VALUES ('FAM-NEG', 'Gorra Roja', 30.0, 60.0, -2, true)
                """);
        jdbcTemplate.update("""
                INSERT INTO productos (codigo, descripcion, precio_costo, precio_minorista, cantidad_stock, activo)
                VALUES ('FAM-NEG', 'Gorra Roja V2', 35.0, 60.0, -5, true)
                """);

        // 3. Inactive product -> must be excluded completely
        jdbcTemplate.update("""
                INSERT INTO productos (codigo, descripcion, precio_costo, precio_minorista, cantidad_stock, activo)
                VALUES ('INACTIVE-1', 'Producto Eliminado', 20.0, 40.0, 50, false)
                """);

        // 4. Generic products (codigo = '1') -> separate items by description
        jdbcTemplate.update("""
                INSERT INTO productos (codigo, descripcion, precio_costo, precio_minorista, cantidad_stock, activo)
                VALUES ('1', 'Generico Tornillo', 5.0, 10.0, 100, true)
                """);
        jdbcTemplate.update("""
                INSERT INTO productos (codigo, descripcion, precio_costo, precio_minorista, cantidad_stock, activo)
                VALUES ('1', 'Generico Tuerca', 3.0, 6.0, 0, true)
                """);

        // Act
        List<CatalogProductDto> catalog = externalCatalogRepository.fetchActiveCatalog();

        // Assert
        // Expected items:
        // 1 - Generico Tornillo (disponible = true)
        // 2 - Generico Tuerca (disponible = false)
        // 3 - FAM-NEG (disponible = false)
        // 4 - FAM-POS (disponible = true)
        // (INACTIVE-1 must not be present)
        assertThat(catalog)
                .hasSize(4)
                .noneMatch(p -> "INACTIVE-1".equals(p.codigo()));

        // Verify FAM-POS: collapsed into 1 item, disponible = true
        List<CatalogProductDto> famPos = catalog.stream().filter(p -> "FAM-POS".equals(p.codigo())).toList();
        assertThat(famPos).hasSize(1);
        assertThat(famPos.getFirst().disponible()).isTrue();

        // Verify FAM-NEG: collapsed into 1 item, disponible = false
        List<CatalogProductDto> famNeg = catalog.stream().filter(p -> "FAM-NEG".equals(p.codigo())).toList();
        assertThat(famNeg).hasSize(1);
        assertThat(famNeg.getFirst().disponible()).isFalse();

        // Verify generic items with code '1' are partitioned by description
        List<CatalogProductDto> generics = catalog.stream().filter(p -> "1".equals(p.codigo())).toList();
        assertThat(generics)
                .hasSize(2)
                .anyMatch(p -> "Generico Tornillo".equals(p.descripcion()) && p.disponible())
                .anyMatch(p -> "Generico Tuerca".equals(p.descripcion()) && !p.disponible());
    }
}
