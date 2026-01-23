package com.centralizesys.service;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.product.Product;
import com.centralizesys.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Test
    @DisplayName("IT-01: Search limits results to 100 items")
    void search_LimitConstraint() {
        // 1. Insert 105 products with similar names

        // Batch insert using Repository to ensure validity
        for (int i = 0; i < 105; i++) {
            Product p = new Product("LIMIT-TEST-" + i, "Common Description Item " + i, 10.0, 10.0, 20.0);
            productRepository.save(p);
        }

        // 2. Search for "Common Description"
        List<Product> results = productService.search("Common Description");

        // 3. Assert
        assertEquals(100, results.size(), "Search should be strictly capped at 100");
    }

    @Test
    @DisplayName("IT-02: Search matches Code OR Description")
    void search_MatchesBoth() {
        createTestProduct("FIND-ME-CODE", 100.0, 0L); // Helper from BaseIntegrationTest

        Product p2 = new Product("HIDDEN-CODE", "FIND-ME-DESC", 10.0, 10.0, 10.0);
        productService.create(p2);

        List<Product> byCode = productService.search("FIND-ME-CODE");
        assertEquals(1, byCode.size());

        List<Product> byDesc = productService.search("FIND-ME-DESC");
        assertEquals(1, byDesc.size());
    }

    @Test
    @DisplayName("IT-03: Delete adheres to DB Cascade constraints (Deletes Stock)")
    void delete_CascadesToStock() {
        // 1. Create Product with Stock
        Long prodId = createTestProduct("CASCADE-TEST", 200.0, 50L);
        Long userId = createTestUser();

        // Check stock exists
        Long stockCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM stock_por_ubicacion WHERE producto_id = ?", Long.class, prodId);
        assertEquals(1, stockCount, "Stock should exist before delete");

        // 2. Delete Product
        productService.deleteById(prodId, userId);

        // 3. Verify Stock is gone (Cascade)
        Long stockAfter = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM stock_por_ubicacion WHERE producto_id = ?", Long.class, prodId);
        assertEquals(0, stockAfter, "Stock should be deleted via Cascade");
    }
}
