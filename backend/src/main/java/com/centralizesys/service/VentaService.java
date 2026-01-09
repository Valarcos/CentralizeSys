package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.product.Product;
import com.centralizesys.model.product.StockLocation;
import com.centralizesys.model.sales.*;
import com.centralizesys.repository.DeudoresRepository;
import com.centralizesys.repository.ProductRepository;
import com.centralizesys.repository.StockRepository;
import com.centralizesys.repository.VentaRepository;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class VentaService {

    private final VentaRepository ventaRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final DeudoresRepository deudoresRepository;
    private final AuditoriaService auditoriaService;

    public VentaService(VentaRepository ventaRepository,
                        ProductRepository productRepository,
                        StockRepository stockRepository,
                        DeudoresRepository deudoresRepository,
                        AuditoriaService auditoriaService) {
        this.ventaRepository = ventaRepository;
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.deudoresRepository = deudoresRepository;
        this.auditoriaService = auditoriaService;
    }

    public List<Venta> getAllVentas() {
        return ventaRepository.findAll();
    }

    public VentaResponse getVentaById(Long id) {
        // 1. Fetch Header
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venta", id));

        // 2. Fetch Details (Items)
        List<DetalleVenta> detalles = ventaRepository.findDetallesByVentaId(id);

        // 3. Fetch Payments
        List<PagoVenta> pagos = ventaRepository.findPagosByVentaId(id);

        // 4. Construct Response (Re-using the DTO)
        return new VentaResponse(
                venta.getId(),
                venta.getFecha(),
                venta.getClienteNombre(),
                venta.getTotalVenta(),
                detalles,
                pagos,
                null // No alerts for historical view
        );
    }

    /**
     * Orchestrates the sales process.
     * High-level manager calling specialized steps.
     */
    @Transactional
    public VentaResponse registrarVenta(VentaRequest request) {
        // 1. Validate
        validateRequest(request);

        // 2. Process Items (Pure logic + DB Reads)
        // Calculates totals and prepares details, but does not write to DB yet.
        ProcessedSaleResult processedData = processItems(request.getItems());

        // 3. Persist Data (Header, Details, Payments)
        // Returns both ID and the processed payments list
        PersistedTransactionInfo txInfo = saveTransactionData(request, processedData);

        // 4. Update Stock (DB Writes - Complex Logic)
        // We use the processed details to avoid fetching products from DB again
        List<String> stockAlerts = updateStockFromDetails(processedData.getDetalles());

        // 5. Handle Debt (Fiados)
        handleDebt(txInfo.getVentaId(), request.getClienteNombre(), processedData.getTotalVenta(), txInfo.getPagosPersistidos());

        // 6. Audit Log (The Sale itself)
        auditoriaService.registrarAccion(
                request.getUsuarioId(),
                "VENTA",
                "Venta ID " + txInfo.getVentaId() + " a " + request.getClienteNombre() + ". Total: $" + processedData.getTotalVenta()
        );

        // 7. Build Response
        return new VentaResponse(
                txInfo.getVentaId(),
                LocalDate.now().toString(),
                request.getClienteNombre(),
                processedData.getTotalVenta(),
                processedData.getDetalles(),
                txInfo.getPagosPersistidos(),
                stockAlerts
        );
    }

    // --- HELPER CLASSES (Internal DTOs) ---

    @Data
    static class ProcessedSaleResult {
        private List<DetalleVenta> detalles;
        private Double totalVenta;
    }

    @Data
    static class PersistedTransactionInfo {
        private Long ventaId;
        private List<PagoVenta> pagosPersistidos;
    }

    // --- HELPER METHODS ---

    private void validateRequest(VentaRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessRuleException("La venta debe tener al menos un producto.");
        }
    }

    /**
     * Processes the list of items to calculate prices, discounts and totals.
     * Does NOT update stock. Separation of concerns.
     */
    // PACKAGE-PRIVATE (Visible to Tests, Hidden from Controller and possible subclasses)
    ProcessedSaleResult processItems(List<VentaRequest.ItemRequest> itemsReq) {
        ProcessedSaleResult result = new ProcessedSaleResult();
        result.setDetalles(new ArrayList<>());
        Double totalAcumulado = 0.0;

        for (VentaRequest.ItemRequest itemReq : itemsReq) {
            // A. Fetch Product
            Product producto = productRepository.findById(itemReq.getProductoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto", itemReq.getProductoId()));

            // B. Build Detail & C. Calculate Prices
            DetalleVenta detalle = createDetalleVenta(producto, itemReq);

            result.getDetalles().add(detalle);
            totalAcumulado += detalle.getSubtotal();
        }
        // We round the total ONCE here. This ensures the Venta Header has a clean value.
        Double totalRounded = Math.round(totalAcumulado * 100.0) / 100.0;
        result.setTotalVenta(totalRounded);

        return result;
    }

    /**
     * Creates a DetalleVenta object with all price calculations applied.
     */
    private DetalleVenta createDetalleVenta(Product producto, VentaRequest.ItemRequest itemReq) {
        DetalleVenta detalle = new DetalleVenta();
        detalle.setProductoId(producto.getId());
        detalle.setCodigoSnapshot(producto.getCodigo());
        detalle.setDescripcionSnapshot(producto.getDescripcion());
        detalle.setCantidad(itemReq.getCantidad());

        // CALCULATE PRICES
        // ---------------------------------------------------------
        Double precioBase = producto.getPrecioMinorista(); // Official DB Price
        detalle.setPrecioLista(precioBase);

        Double valorDescuento = itemReq.getValorDescuento() != null ? itemReq.getValorDescuento() : 0.0;

        // Logic to calculate final price
        Double precioFinal = calculateFinalPrice(precioBase, valorDescuento, producto.getDescripcion());

        detalle.setDescuentoValor(valorDescuento);
        detalle.setPrecioUnitario(precioFinal);
        // ---------------------------------------------------------

        Double subtotal = itemReq.getCantidad() * precioFinal;
        detalle.setSubtotal(subtotal);

        return detalle;
    }

    /**
     * Helper to handle the math and validation of discounts
     */
    private Double calculateFinalPrice(Double basePrice, Double value, String productName) {
        if (value < 0) {
            throw new BusinessRuleException("El descuento no puede ser negativo para: " + productName);
        }

        if (value > basePrice) {
            throw new BusinessRuleException("El monto de descuento ($" + value + ") no puede ser mayor al precio del producto ($" + basePrice + ") para: " + productName);
        }

        Double finalPrice = basePrice - value;

        // Round to 2 decimals to avoid floating point errors (e.g. 99.999999)
        return Math.round(finalPrice * 100.0) / 100.0;
    }

    private PersistedTransactionInfo saveTransactionData(VentaRequest request, ProcessedSaleResult processedData) {
        PersistedTransactionInfo info = new PersistedTransactionInfo();

        // A. Header
        Venta venta = new Venta();
        venta.setFecha(LocalDate.now().toString()); // Result: "ej.: 2025-12-14"
        venta.setClienteNombre(request.getClienteNombre());
        venta.setTotalVenta(processedData.getTotalVenta());
        // If the frontend sends null, this will be null in DB (allowed by schema if not strict, but good for traceability)
        venta.setUsuarioId(request.getUsuarioId());

        Long ventaId = ventaRepository.saveVenta(venta);
        info.setVentaId(ventaId);

        // B. Details
        processedData.getDetalles().forEach(d -> d.setVentaId(ventaId));
        ventaRepository.saveDetalles(processedData.getDetalles());

        // C. Payments
        if (request.getPagos() != null && !request.getPagos().isEmpty()) {
            List<PagoVenta> pagosEntities = new ArrayList<>();
            for (VentaRequest.PagoRequest p : request.getPagos()) {
                pagosEntities.add(new PagoVenta(null, ventaId, p.getMetodoPagoId(), p.getMonto()));
            }
            ventaRepository.savePagos(pagosEntities);
            info.setPagosPersistidos(pagosEntities);
        } else {
            info.setPagosPersistidos(Collections.emptyList());
        }

        return info;
    }

    /**
     * Handles the physical stock deduction.
     * Iterates over the Processed Details to avoid re-fetching products from DB.
     */
    // PACKAGE-PRIVATE (Visible to Tests, Hidden from Controller and possible subclasses)
    List<String> updateStockFromDetails(List<DetalleVenta> detalles) {
        List<String> alerts = new ArrayList<>();

        for (DetalleVenta detalle : detalles) {
            // D. Deduct Stock
            // We use detail.getDescripcionSnapshot() as the product name for alerts
            String alerta = deductStockFromInventory(detalle.getProductoId(), detalle.getDescripcionSnapshot(), detalle.getCantidad());

            if (alerta != null) {
                alerts.add(alerta);
            }
        }
        return alerts;
    }

    /**
     * ATOMIC DECREMENT LOGIC
     * Manages stock deduction across multiple locations.
     * If stock is not enough, it forces a negative balance on the first available location.
     */
    // PACKAGE-PRIVATE (Visible to Tests, Hidden from Controller and possible subclasses)
    String deductStockFromInventory(Long productId, String productName, Long quantityNeeded) {
        List<StockLocation> locations = stockRepository.findByProductId(productId);
        Long remainingToDeduct = quantityNeeded;

        for (StockLocation loc : locations) {
            if (remainingToDeduct <= 0) break;
            Long available = loc.getCantidad();

            // We take from this box if it has positive stock
            if (available > 0) {
                Long toTake = Math.min(available, remainingToDeduct);
                stockRepository.subtractStock(loc.getUbicacionId(), productId, toTake);
                remainingToDeduct -= toTake;
            }
        }

        // If we still need to deduct stock (e.g. need 5, only found 3, remaining is 2)
        if (remainingToDeduct > 0) {
            if (locations.isEmpty()) {
                // Scenario: Product exists in DB but has NO entry in 'stock_por_ubicacion'
                return "CRÍTICO: El producto '" + productName + "' se vendió pero NO tiene ubicación de stock asignada.";
            } else {
                // Scenario: Product exists in 'stock_por_ubicacion' but sum is 0 or low.
                // We force the subtraction on the first location found, making it negative.
                Long defaultLocId = locations.getFirst().getUbicacionId();
                stockRepository.subtractStock(defaultLocId, productId, remainingToDeduct);
                return "ATENCIÓN: Stock insuficiente para '" + productName + "'. El sistema registró stock negativo.";
            }
        }
        return null;
    }

    private void handleDebt(Long ventaId, String clienteNombre, Double totalVenta, List<PagoVenta> pagosPersistidos) {
        // 1. Sum payments (Might have tiny noise like 50.1000000004, but that is okay)
        Double totalPagado = pagosPersistidos.stream()
                .mapToDouble(PagoVenta::getMonto)
                .sum();

        // 2. Calculate difference
        Double deuda = totalVenta - totalPagado;

        // 3. Epsilon Check
        // Since EPSILON (0.0001) is much larger than math noise (0.00000000001),
        // this check safely filters out false positives without needing to round 'totalPagado'.
        Double epsilon = 0.0001;

        if (deuda > epsilon) {
            if (clienteNombre == null || clienteNombre.isBlank()) {
                throw new BusinessRuleException("Para dejar una deuda (Fiado), se requiere el nombre del cliente.");
            }

            // 4. Final Rounding
            // We only round here to ensure the database gets a clean "5.50" instead of "5.499999"
            double deudaFinal = Math.round(deuda * 100.0) / 100.0;

            deudoresRepository.save(ventaId, clienteNombre, deudaFinal);
        }
    }
}