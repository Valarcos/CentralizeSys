package com.centralizesys;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.product.Product;
import com.centralizesys.repository.ProductRepository;
import com.centralizesys.service.AuditoriaService;
import com.centralizesys.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductServiceTest {

    private ProductRepository repository;
    private ProductService service;
    private AuditoriaService auditoriaService;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(ProductRepository.class);
        service = new ProductService(repository, auditoriaService);
    }

    @Test
    void testGetAll() {
        Product p = new Product(1L, "CODE1", "Remera Test", 500.0, 800.0, 1000.0, 10L);
        when(repository.findAll()).thenReturn(List.of(p));

        List<Product> result = service.getAll();

        assertEquals(1, result.size());
        assertEquals("Remera Test", result.getFirst().getDescripcion());
    }

    @Test
    void testGetByIdFound() {
        Product p = new Product(1L, "CODE1", "Remera Test", 500.0, 800.0, 1000.0, 10L);
        when(repository.findById(1L)).thenReturn(Optional.of(p));

        Product result = service.getById(1L);

        assertEquals("Remera Test", result.getDescripcion());
    }

    @Test
    void testGetByIdNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getById(99L));
    }

    @Test
    void testCreateSuccess_NewCode() {
        // [SCENARIO] Creating a product with a code that effectively doesn't exist
        Product p = new Product("CODE_NEW", "Nueva Remera", 400.0, 700.0, 900.0);

        // [FIXED] Mock returning empty List instead of Optional.empty()
        when(repository.findAllByCodigo("CODE_NEW")).thenReturn(Collections.emptyList());
        when(repository.save(p)).thenReturn(p);

        service.create(p);

        verify(repository, times(1)).save(p);
    }

    @Test
    void testCreateSuccess_VariantAllowed() {
        // [SCENARIO] Code exists, but COST is different. Should allow creation (Variant).
        Product newVariant = new Product("SHIRT", "Remera Blue", 600.0, 0.0, 1200.0);

        // Existing is cheaper ($500)
        Product existing = new Product(1L, "SHIRT", "Remera Old", 500.0, 0.0, 1000.0, 5L);

        // [FIXED] Return List containing the existing item
        when(repository.findAllByCodigo("SHIRT")).thenReturn(List.of(existing));
        when(repository.save(newVariant)).thenReturn(newVariant);

        assertDoesNotThrow(() -> service.create(newVariant));
        verify(repository, times(1)).save(newVariant);
    }

    @Test
    void testCreateDuplicateVariantThrowsException() {
        // [SCENARIO] Exact duplicate (Same Code, Same Cost, Same Price) -> BLOCK
        Product duplicate = new Product("SHIRT", "Remera", 500.0, 0.0, 1000.0);
        Product existing = new Product(1L, "SHIRT", "Remera Old", 500.0, 0.0, 1000.0, 5L);

        when(repository.findAllByCodigo("SHIRT")).thenReturn(List.of(existing));

        assertThrows(BusinessRuleException.class, () -> service.create(duplicate));
        verify(repository, never()).save(any());
    }

    @Test
    void testCreateCode1Allowed() {
        // [SCENARIO] "1" is generic, exact duplicates allowed
        Product p = new Product("1", "Genérico", 100.0, 200.0, 300.0);
        Product existing = new Product(55L, "1", "Otro Genérico", 100.0, 200.0, 300.0, 10L);

        when(repository.findAllByCodigo("1")).thenReturn(List.of(existing));
        when(repository.save(p)).thenReturn(p);

        assertDoesNotThrow(() -> service.create(p));
    }

    @Test
    void testUpdateNotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        Product updated = new Product("UPDATED", "Desc", 100.0, 200.0, 300.0);

        assertThrows(ResourceNotFoundException.class, () -> service.update(1L, updated));
    }
}