package com.centralizesys.service;

import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.Product;
import com.centralizesys.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    public List<Product> getAll() {
        return repository.findAll();
    }

    public Product getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product ", id));
    }

    public Product create(Product product) {
        return repository.save(product);
    }

    public void update(Long id, Product product) {
        if (repository.findById(id).isEmpty()) {
            throw new ResourceNotFoundException("Product", id);
        }
        repository.save(product);
    }

    public void deleteById(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Product", id);
        }
        repository.deleteById(id);
    }
}
