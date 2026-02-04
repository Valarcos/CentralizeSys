package com.centralizesys.controller;

import com.centralizesys.model.product.Product;
import com.centralizesys.model.product.ProductRequest;
import com.centralizesys.model.product.ProductResponse;
import com.centralizesys.security.SecurityUtils;
import com.centralizesys.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    // GET /api/productos?search=...
    // Combines "GetAll" and "Search" into one intuitive endpoint
    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllOrSearch(
            @RequestParam(required = false) String search) {

        List<Product> products;

        if (search != null && !search.isBlank()) {
            products = service.search(search);
        } else {
            products = service.getAll();
        }

        List<ProductResponse> response = products.stream()
                .map(ProductResponse::new)
                .toList();

        return ResponseEntity.ok(response);
    }

    // GET /api/productos/{id}
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(@PathVariable Long id) {
        Product product = service.getById(id);
        return ResponseEntity.ok(new ProductResponse(product));
    }

    // POST /api/productos
    @PostMapping
    public ResponseEntity<ProductResponse> create(@RequestBody ProductRequest request) {
        // Map DTO -> Model
        Product newProduct = new Product(
                request.getCodigo(),
                request.getDescripcion(),
                request.getPrecioCosto(),
                request.getPrecioMayorista(),
                request.getPrecioMinorista());

        Product saved = service.create(newProduct);
        return new ResponseEntity<>(new ProductResponse(saved), HttpStatus.CREATED);
    }

    // PUT /api/productos/{id}
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable Long id,
                                       @RequestBody ProductRequest request) {
        // Map DTO -> Model
        Product updatedProduct = new Product(
                request.getCodigo(),
                request.getDescripcion(),
                request.getPrecioCosto(),
                request.getPrecioMayorista(),
                request.getPrecioMinorista());

        service.update(id, updatedProduct);
        return ResponseEntity.noContent().build();
    }

    // DELETE /api/productos/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Long usuarioId = SecurityUtils.getAuthenticatedUserId();
        service.deleteById(id, usuarioId); // Pass it down
        return ResponseEntity.noContent().build();
    }

    // GET /api/productos/alerts
    // Returns products with negative stock for Dashboard warning
    @GetMapping("/alerts")
    public ResponseEntity<List<ProductResponse>> getLowStockAlerts() {
        List<Product> alerts = service.getLowStockAlerts();
        return ResponseEntity.ok(alerts.stream()
                .map(ProductResponse::new)
                .toList());
    }
}