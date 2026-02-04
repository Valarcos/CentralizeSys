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
}
