package com.centralizesys;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.product.Product;
import com.centralizesys.repository.ProductRepository;
import com.centralizesys.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductServiceTest {

    private ProductRepository repository;
    private ProductService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(ProductRepository.class);
        service = new ProductService(repository);
    }

    @Test
    void testGetAll() {
        // Create a dummy product using the NEW Constructor
        // Product(id, codigo, descripcion, precioCosto, precioMayorista, precioMinorista, stock)
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
    void testCreateSuccess() {
        // Create new product (ID null)
        Product p = new Product("CODE_NEW", "Nueva Remera", 400.0, 700.0, 900.0);

        // Mock that the code does NOT exist yet
        when(repository.findByCodigo("CODE_NEW")).thenReturn(Optional.empty());

        // Mock returning the saved object
        when(repository.save(p)).thenReturn(p);

        service.create(p);

        verify(repository, times(1)).save(p);
    }

    @Test
    void testCreateDuplicateCodeThrowsException() {
        Product p = new Product("DUPLICATE", "Remera", 400.0, 700.0, 900.0);
        Product existing = new Product(1L, "DUPLICATE", "Old", 400.0, 700.0, 900.0, 5L);

        // Mock that the code ALREADY exists
        when(repository.findByCodigo("DUPLICATE")).thenReturn(Optional.of(existing));

        // Must throw BusinessRuleException
        assertThrows(BusinessRuleException.class, () -> service.create(p));

        // Ensure we never called save
        verify(repository, never()).save(any());
    }

    @Test
    void testCreateCode1Allowed() {
        // "1" is the generic code, duplicates are allowed
        Product p = new Product("1", "Genérico", 100.0, 200.0, 300.0);
        Product existing = new Product(55L, "1", "Otro Genérico", 100.0, 200.0, 300.0, 10L);

        // Even if "1" exists...
        when(repository.findByCodigo("1")).thenReturn(Optional.of(existing));
        when(repository.save(p)).thenReturn(p);

        // ...creation should proceed without error
        assertDoesNotThrow(() -> service.create(p));

        verify(repository, times(1)).save(p);
    }

    @Test
    void testUpdateNotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        Product updated = new Product("UPDATED", "Desc", 100.0, 200.0, 300.0);

        assertThrows(ResourceNotFoundException.class, () -> service.update(1L, updated));
    }
}