package com.centralizesys.service;

import com.centralizesys.model.product.Product;
import com.centralizesys.model.purchase.Compra;
import com.centralizesys.model.purchase.CompraItemRequest;
import com.centralizesys.model.purchase.CompraRequest;
import com.centralizesys.model.purchase.DetalleCompra;
import com.centralizesys.model.purchase.CompraResponse;
import com.centralizesys.model.purchase.DetalleCompraResponse;
import com.centralizesys.repository.CompraRepository;
import com.centralizesys.repository.ProductRepository;
import com.centralizesys.repository.StockRepository;
import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.exception.ResourceNotFoundException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CompraService {

    private final CompraRepository compraRepository;
    private final StockRepository stockRepository;
    private final ProductRepository productRepository;

    public CompraService(CompraRepository compraRepository,
                         StockRepository stockRepository,
                         ProductRepository productRepository) {
        this.compraRepository = compraRepository;
        this.stockRepository = stockRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public CompraResponse registrarCompra(CompraRequest request) {
        // 1. Validate Payload
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessRuleException("La compra debe tener al menos un producto.");
        }

        // 2. Batch Fetch Products (Prevent N+1 Queries)
        // We collect IDs first to make a single DB call
        List<Long> productIds = request.getItems().stream()
                .map(CompraItemRequest::getProductoId)
                .distinct()
                .toList();

        Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        // 3. Processing Loop (Validate, Calculate, Update Stock)
        List<DetalleCompra> detallesToSave = new ArrayList<>();
        List<DetalleCompraResponse> itemsResponse = new ArrayList<>();
        Double totalCompra = 0.0;

        for (CompraItemRequest itemReq : request.getItems()) {
            Product product = productMap.get(itemReq.getProductoId());

            // A. Existence Check
            if (product == null) {
                throw new ResourceNotFoundException("Producto", itemReq.getProductoId());
            }

            // B. Cost Consistency Check (Variant Logic)
            // The DB dictates the cost. The request must match the DB cost for this specific ID.
            if (!compareDouble(itemReq.getCostoUnitario(), product.getPrecioCosto())) {
                throw new BusinessRuleException(String.format(
                        "Inconsistencia de Costos: El producto '%s' (ID: %d) tiene un costo registrado de $%.2f, pero se intentó comprar a $%.2f. Seleccione la variante correcta.",
                        product.getDescripcion(), product.getId(), product.getPrecioCosto(), itemReq.getCostoUnitario()
                ));
            }

            // C. Calculations
            Double subtotal = itemReq.getCantidad() * itemReq.getCostoUnitario();
            totalCompra += subtotal;

            // D. Prepare Detail Entity (For DB)
            DetalleCompra detalle = new DetalleCompra();
            detalle.setProductoId(product.getId());
            detalle.setCantidad(itemReq.getCantidad());
            detalle.setCostoUnitario(itemReq.getCostoUnitario());
            detalle.setSubtotal(subtotal);
            detallesToSave.add(detalle);

            // E. Prepare Response Item (For UI)
            itemsResponse.add(new DetalleCompraResponse(
                    product.getCodigo(),
                    product.getDescripcion(),
                    itemReq.getCantidad(),
                    itemReq.getCostoUnitario(),
                    subtotal
            ));

            // F. Stock Update
            // We do this immediately to ensure the location exists before saving the purchase.
            try {
                stockRepository.addStock(product.getId(), itemReq.getUbicacionId(), itemReq.getCantidad());
            } catch (DataAccessException e) {
                throw new BusinessRuleException("Error al agregar stock: Verifique que la Ubicación ID " + itemReq.getUbicacionId() + " exista.");
            }
        }

        // 4. Persist Header (Compra)
        // Note: Repository generates LocalDate.now() internally, but we capture strict time here
        // to ensure the response matches logically.
        String fechaActual = LocalDate.now().toString();

        Compra compra = new Compra();
        compra.setFecha(fechaActual);
        compra.setProveedor(request.getProveedor());
        compra.setNroComprobante(request.getNroComprobante());
        compra.setUsuarioId(request.getUsuarioId());
        compra.setTotalCompra(totalCompra);

        // Returns the ID from the GeneratedKeyHolder
        Long compraId = compraRepository.saveCompra(compra);

        // 5. Persist Details (Batch Insert)
        // Link the new Compra ID to the details
        detallesToSave.forEach(d -> d.setCompraId(compraId));
        compraRepository.saveDetalles(detallesToSave);

        // 6. Return Response
        return new CompraResponse(
                compraId,
                fechaActual,
                request.getProveedor(),
                request.getNroComprobante(),
                totalCompra,
                itemsResponse
        );
    }

    /**
     * Helper to safely compare doubles for currency equality.
     * Returns true if difference is less than 0.001.
     */
    private boolean compareDouble(Double a, Double b) {
        if (a == null || b == null) return false;
        return Math.abs(a - b) < 0.001;
    }
}