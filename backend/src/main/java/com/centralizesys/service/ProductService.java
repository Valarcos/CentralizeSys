package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.product.Product;
import com.centralizesys.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository repository;
    private final AuditoriaService auditoriaService; // [NEW DEPENDENCY]

    private static final String PRODUCT = "Product";

    public ProductService(ProductRepository repository, AuditoriaService auditoriaService) {
        this.repository = repository;
        this.auditoriaService = auditoriaService;
    }

    public List<Product> getAll() {
        return repository.findAll();
    }

    public Product getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(PRODUCT, id));
    }

    // Specific method to retrieve strict matches for logic/validation
    public List<Product> getVariantsByCode(String codigo) {
        return repository.findAllByCodigo(codigo);
    }

    // Uses the "Smart Search" (Code OR Description)
    public List<Product> search(String query) {
        return repository.search(query);
    }

    // Package-private for testing
    void validate(Product product) {
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
        checkVariantCollision(product, null);
        return repository.save(product);
    }

    public void update(Long id, Product product) {
        // 1. Basic Validation
        validate(product);

        // 2. Fetch Existing Data
        Product existingProduct = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(PRODUCT, id));

        // 3. Unique Constraint Check
        // We only check for collision if relevant fields changed
        boolean codeChanged = !product.getCodigo().equals(existingProduct.getCodigo());
        boolean costChanged = !compareDouble(product.getPrecioCosto(), existingProduct.getPrecioCosto());
        boolean priceChanged = !compareDouble(product.getPrecioMinorista(), existingProduct.getPrecioMinorista());

        if (codeChanged || costChanged || priceChanged) {
            checkVariantCollision(product, id);
        }

        // 4. Attach ID and Persist
        product.setId(id);
        repository.save(product);
    }

    // Extracted logic to avoid duplication
    // excludeId should be null for create, and the current ID for update
    private void checkVariantCollision(Product product, Long excludeId) {
        // Generic code "1" bypasses all uniqueness checks
        if ("1".equals(product.getCodigo())) {
            return;
        }

        List<Product> variants = repository.findAllByCodigo(product.getCodigo());

        boolean collision = variants.stream()
                .filter(p -> !p.getId().equals(excludeId)) // Exclude self if updating (safe if excludeId is null)
                .anyMatch(p -> compareDouble(p.getPrecioCosto(), product.getPrecioCosto()) &&
                        compareDouble(p.getPrecioMinorista(), product.getPrecioMinorista()));

        if (collision) {
            throw new BusinessRuleException("Ya existe otra variante exacta con Código: " + product.getCodigo()
                    + ", Costo: " + product.getPrecioCosto()
                    + ", Precio: " + product.getPrecioMinorista());
        }
    }

    // Helper to avoid floating point comparison issues for double comparison
    // (precision safety)
    // Package-private for testing
    boolean compareDouble(Double a, Double b) {
        if (a == null || b == null)
            return false;
        return Math.abs(a - b) < 0.001;
    }

    // [CHANGED SIGNATURE] Added usuarioId
    public void deleteById(Long id, Long usuarioId) {
        // Note: The DB handles cascading delete of stock_por_ubicacion
        // But we check existence first to throw a proper 404 if needed
        Product p = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(PRODUCT, id)); // Check existence to get name for log

        repository.deleteById(id);

        // Log the action with description for clarity
        auditoriaService.registrarAccion(usuarioId, "DELETE_PRODUCT",
                "Eliminado producto ID " + id + ": " + p.getDescripcion());
    }

    /**
     * Get products with negative stock for Dashboard "Morning Warning".
     */
    public List<Product> getLowStockAlerts() {
        return repository.findLowStock();
    }
}