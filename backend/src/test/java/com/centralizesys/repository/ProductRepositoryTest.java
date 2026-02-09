package com.centralizesys.repository;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.product.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    @DisplayName("Save inserts a new product and returns Generated ID")
    void save_InsertsNewProduct() {
        // Manually create object WITHOUT ID for save test
        Product p = new Product("A-001", "Test Product", 50.0, 80.0, 100.0);

        Product saved = productRepository.save(p);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCodigo()).isEqualTo("A-001");
    }

    @Test
    @DisplayName("FindAll with Limit/Offset returns correct page")
    void findAll_Paged_ReturnsCorrectSubset() {
        // Arrange: Create 25 products
        for (int i = 0; i < 25; i++) {
            createTestProduct("PAGE-" + i, 100.0 + i, 10L);
        }

        // Act: Request Page 2 (Limit 10, Offset 10) -> Should get 10 items (Indices
        // 10-19)
        List<Product> page2 = productRepository.findAll(10L, 10L);

        // Assert
        assertThat(page2).hasSize(10);
    }

    @Test
    @DisplayName("CountAll returns total number of products")
    void countAll_ReturnsCorrectCount() {
        createTestProduct("CNT-1", 100.0, 10L);
        createTestProduct("CNT-2", 100.0, 10L);
        createTestProduct("CNT-3", 100.0, 10L);

        Long count = productRepository.countAll();

        assertThat(count).isEqualTo(3L);
    }

    @Test
    @DisplayName("FindAll returns all products")
    void findAll_ReturnsAll() {
        createTestProduct("A-001", 100.0, 10L);
        createTestProduct("A-002", 200.0, 5L);

        List<Product> all = productRepository.findAll();

        assertThat(all).hasSize(2);
    }

    @Test
    @DisplayName("FindById returns correct product")
    void findById_ReturnsCorrectProduct() {
        Long id = createTestProduct("B-001", 150.0, 20L);

        Optional<Product> found = productRepository.findById(id);

        assertThat(found).isPresent();
        assertThat(found.get().getCodigo()).isEqualTo("B-001");
    }

    @Test
    @DisplayName("Update modifies existing product")
    void update_ModifiesProduct() {
        Long id = createTestProduct("C-001", 300.0, 0L);
        Product saved = productRepository.findById(id).orElseThrow();

        saved.setDescripcion("Updated Description");
        productRepository.save(saved); // Should trigger update because ID is present

        Product updated = productRepository.findById(id).orElseThrow();
        assertThat(updated.getDescripcion()).isEqualTo("Updated Description");
    }

    @Test
    @DisplayName("Delete removes product")
    void delete_RemovesProduct() {
        Long id = createTestProduct("D-001", 50.0, 5L);

        productRepository.deleteById(id);

        Optional<Product> found = productRepository.findById(id);
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Search returns matching products")
    void search_ReturnsMatches() {
        // Create first product via helper
        Long id1 = createTestProduct("SEARCH-1", 10.0, 10L);

        // Create second product manually to specify description
        Product p2 = new Product("SEARCH-2", "Orange Juice", 20.0, null, 40.0);
        productRepository.save(p2);

        // Update the first one to have "Apple Juice"
        Product p1 = productRepository.findById(id1).orElseThrow();
        p1.setDescripcion("Apple Juice");
        productRepository.save(p1);

        List<Product> results = productRepository.search("Apple");

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getCodigo()).isEqualTo("SEARCH-1");
    }

    @Test
    @DisplayName("findLowStock - returns products with negative stock")
    void findLowStock_returnsNegativeStockProducts() {
        // Arrange - Create a product with negative stock
        // First create product with 0 stock
        Long productId = createTestProduct("LOW-001", 100.0, 0L);

        // Manually set negative stock by subtracting more than available
        jdbcTemplate.update(
                "UPDATE productos SET cantidad_stock = -5 WHERE id = ?",
                productId);

        // Act
        List<Product> lowStock = productRepository.findLowStock();

        // Assert
        assertThat(lowStock).hasSize(1);
        assertThat(lowStock.getFirst().getCantidadStock()).isNegative();
    }

    @Test
    @DisplayName("findLowStock - returns empty when no negative stock")
    void findLowStock_returnsEmptyWhenNoNegativeStock() {
        // Arrange - Create products with positive stock only
        createTestProduct("POS-001", 100.0, 10L);
        createTestProduct("POS-002", 200.0, 5L);

        // Act
        List<Product> lowStock = productRepository.findLowStock();

        // Assert
        assertThat(lowStock).isEmpty();
    }
}
