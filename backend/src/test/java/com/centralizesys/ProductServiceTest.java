package com.centralizesys;

import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.Product;
import com.centralizesys.repository.ProductRepository;
import com.centralizesys.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        Product p = new Product(1L, "Test", 5, 100.0);
        when(repository.findAll()).thenReturn(List.of(p));

        List<Product> result = service.getAll();

        assertEquals(1, result.size());
        assertEquals("Test", result.getFirst().getName());
    }

    @Test
    void testGetByIdFound() {
        Product p = new Product(1L, "Test", 5, 100.0);
        when(repository.findById(1L)).thenReturn(Optional.of(p));

        Product result = service.getById(1L);

        assertEquals("Test", result.getName());
    }

    @Test
    void testGetByIdNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getById(99L));
    }

    @Test
    void testCreate() {
        Product p = new Product(null, "New", 3, 99.0);
        // Mock returning the same object (simulating a successful save)
        when(repository.save(p)).thenReturn(p);

        service.create(p);

        verify(repository, times(1)).save(p);
    }

    @Test
    void testUpdateNotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        Product updated = new Product(null, "Updated", 10, 199.0);

        assertThrows(ResourceNotFoundException.class, () -> service.update(1L, updated));
    }

    @Test
    void testDeleteNotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.deleteById(1L));
    }
}
