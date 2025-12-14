package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.Product;
import com.centralizesys.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

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
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    // Uses the "Smart Search" (Code OR Description)
    public List<Product> search(String query) {
        return repository.search(query);
    }

    private void validate(Product product) {
        if (product.getCodigo() == null || product.getCodigo().isBlank()) {
            throw new BusinessRuleException("El código del producto (ART) es obligatorio.");
        }
        if (product.getDescripcion() == null || product.getDescripcion().isBlank()) {
            throw new BusinessRuleException("La descripción es obligatoria.");
        }
        if (product.getPrecioMinorista() == null || product.getPrecioMinorista() < 0) {
            throw new BusinessRuleException("El precio de venta minorista debe ser 0 o mayor.");
        }

        // Allow null, but if it exists, it must be >= 0
        if (product.getPrecioMayorista() != null && product.getPrecioMayorista() < 0) {
            throw new BusinessRuleException("El precio de venta mayorista debe ser 0 o mayor.");
        }
        if (product.getPrecioCosto() == null || product.getPrecioCosto() < 0) {
            throw new BusinessRuleException("El costo debe ser 0 o mayor.");
        }
    }

    public Product create(Product product) {
        validate(product);

        // Open the box to see if there is a product inside
        Optional<Product> existingOpt = repository.findByCodigo(product.getCodigo());

        // PRESERVED LOGIC: Allow duplicates ONLY if code is "1"
        if (existingOpt.isPresent() && !"1".equals(product.getCodigo())) {
            throw new BusinessRuleException("Ya existe un producto con el código: " + product.getCodigo());
        }

        return repository.save(product);
    }

    public void update(Long id, Product product) {
        // 1. Basic Validation
        validate(product);

        // 2. Fetch Existing Data (Required to compare codes)
        Product existingProduct = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        // 3. Check for Code Changes (Unique Constraint Logic)
        String newCode = product.getCodigo();
        String oldCode = existingProduct.getCodigo();

        // PRESERVED LOGIC: Check collision only if code changed and is not "1"
        if (!newCode.equals(oldCode) && !"1".equals(newCode)) {
            repository.findByCodigo(newCode).ifPresent(collision -> {
                throw new BusinessRuleException("El código " + newCode + " ya está en uso por otro producto.");
            });
        }


        // 4. Attach the ID to the incoming object
        // CRITICAL: The 'product' object coming from the Controller has ID = null.
        // We must set it here so the Repository knows to perform an UPDATE (WHERE id = X), not an INSERT.
        product.setId(id);

        // 5. Persist
        repository.save(product);
    }

    public void deleteById(Long id) {
        // Note: The DB handles cascading delete of stock_por_ubicacion
        // But we check existence first to throw a proper 404 if needed
        if (repository.findById(id).isEmpty()) {
            throw new ResourceNotFoundException("Product", id);
        }
        repository.deleteById(id);
    }
}