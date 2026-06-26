package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.dto.PageResponse;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    private static final String ANULADA = "ANULADA";

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

    public PageResponse<Venta> getVentasPage(String startDate, String endDate, int page,
                                             int size) {
        // 1. Set Defaults (Last 30 Days)
        LocalDateTime end = (endDate == null || endDate.isBlank()) ? LocalDateTime.now(ZoneId.systemDefault()) : LocalDate.parse(endDate).atTime(23, 59, 59, 999999999);
        LocalDateTime start = (startDate == null || startDate.isBlank()) ? end.minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0) : LocalDate.parse(startDate).atStartOfDay();

        // 2. Validate Range (Max 60 Days)
        long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(start.atZone(ZoneId.systemDefault()), end.atZone(ZoneId.systemDefault()));
        if (daysDiff < 0) {
            throw new BusinessRuleException("La fecha de inicio no puede ser posterior a la fecha de fin.");
        }
        if (daysDiff > 60) {
            throw new BusinessRuleException(
                    "El rango de fechas no puede exceder los 60 días. (Seleccionado: " + daysDiff + " días)");
        }

        // 3. Pagination
        int offset = page * size;

        // 4. Fetch
        List<Venta> ventas = ventaRepository.findVentasByFechaBetween(start, end, size, offset);
        long totalElements = ventaRepository.countVentasByFechaBetween(start, end);
        long totalPages = (long) Math.ceil((double) totalElements / size);

        return new com.centralizesys.model.dto.PageResponse<>(ventas, (long) page, (long) size, totalElements,
                totalPages);
    }

    public List<String> getClientes() {
        return ventaRepository.findDistinctClientNames();
    }

    public VentaResponse getVentaById(Long id) {
        // 1. Fetch Header
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venta", id));

        // 2. Fetch Details (Items)
        List<DetalleVenta> detalles = ventaRepository.findDetallesByVentaId(id);

        // 3. Fetch Payments
        List<PagoVenta> pagos = ventaRepository.findPagosByVentaId(id);

        // 4. Fetch Seller Name by resolving usuario_id -> nombre
        String vendedorNombre = ventaRepository.findVendedorNombre(venta.getUsuarioId());

        // 5. Construct Response
        return new VentaResponse(
                venta.getId(),
                venta.getFecha(),
                venta.getClienteNombre(),
                vendedorNombre,
                venta.getTotalVenta(),
                venta.getDescuentoGlobal(),
                venta.getTipoVenta(),
                detalles,
                pagos,
                null, // No alerts for historical view
                venta.getEstado()
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
        ProcessedSaleResult processedData = processItems(request.getItems(), request.getTipoVenta());

        // Apply Global Discount
        // Logic: Total = Sum(Items) - GlobalDiscount
        // Ensure total doesn't go below 0
        Double subtotal = processedData.getTotalVenta();
        Double descuentoGlobal = request.getDescuentoGlobal() != null ? request.getDescuentoGlobal() : 0.0;

        if (descuentoGlobal < 0) {
            throw new BusinessRuleException("El descuento global no puede ser negativo.");
        }
        if (descuentoGlobal > subtotal) {
            throw new BusinessRuleException("El descuento global ($" + descuentoGlobal
                    + ") no puede ser mayor al subtotal ($" + subtotal + ").");
        }

        // Update Total with Discount
        // We persist the FINAL TOTAL in 'total_venta' column, but we might want to
        // store subtotal?
        // Schema says: total_venta REAL NOT NULL.
        // We added descuento_global column.
        // So let's say total_venta is the FINAL amount to pay.
        // And we store descuento_global separately.
        Double finalTotal = Math.round((subtotal - descuentoGlobal) * 100.0) / 100.0;

        processedData.setTotalVenta(finalTotal);
        processedData.setDescuentoGlobal(descuentoGlobal);

        // 3. Persist Data (Header, Details, Payments)
        // Returns both ID and the processed payments list
        PersistedTransactionInfo txInfo = saveTransactionData(request, processedData);

        // 4. Update Stock (DB Writes - Complex Logic)
        // We use the processed details to avoid fetching products from DB again
        List<String> stockAlerts = updateStockFromDetails(processedData.getDetalles());

        // 5. Handle Debt (Fiados)
        handleDebt(txInfo.getVentaId(), request.getClienteNombre(), processedData.getTotalVenta(),
                txInfo.getPagosPersistidos());

        // 6. Audit Log (The Sale itself)
        auditoriaService.registrarAccion(
                request.getUsuarioId(),
                "VENTA",
                "Venta ID " + txInfo.getVentaId() + " a " + request.getClienteNombre() + ". Total: $"
                        + processedData.getTotalVenta() + " (Desc: " + descuentoGlobal + ")");

        // 7. Build Response
        // Resolve the seller name from the user who made the sale
        String vendedorNombre = ventaRepository.findVendedorNombre(request.getUsuarioId());
        return new VentaResponse(
                txInfo.getVentaId(),
                LocalDateTime.now(ZoneId.systemDefault()),
                request.getClienteNombre(),
                vendedorNombre,
                processedData.getTotalVenta(),
                descuentoGlobal,
                request.getTipoVenta() != null ? request.getTipoVenta().name() : "MINORISTA",
                processedData.getDetalles(),
                txInfo.getPagosPersistidos(),
                stockAlerts,
                "ACTIVA");
    }

    // --- HELPER CLASSES (Internal DTOs) ---

    @Data
    static class ProcessedSaleResult {
        private Double totalVenta;
        private List<DetalleVenta> detalles;
        private Double descuentoGlobal;
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
    // PACKAGE-PRIVATE (Visible to Tests, Hidden from Controller and possible
    // subclasses)
    ProcessedSaleResult processItems(List<VentaRequest.ItemRequest> itemsReq, TipoVenta tipoVenta) {
        ProcessedSaleResult result = new ProcessedSaleResult();
        result.setDetalles(new ArrayList<>());
        Double totalAcumulado = 0.0;

        for (VentaRequest.ItemRequest itemReq : itemsReq) {
            // A. Fetch Product (repository filters activo=true, so deleted products → empty)
            Product producto = productRepository.findById(itemReq.getProductoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto", itemReq.getProductoId()));

            // B. Active-product guard: defence-in-depth to produce a precise error message.
            // A deleted product should never appear in a sale — it is archived, not missing.
            if (!producto.isActivo()) {
                throw new BusinessRuleException(
                        "El producto '" + producto.getDescripcion() + "' (ID: " + producto.getId()
                                + ") está eliminado y no puede ser incluido en una venta.");
            }

            // C. Build Detail & D. Calculate Prices
            DetalleVenta detalle = createDetalleVenta(producto, itemReq, tipoVenta);

            result.getDetalles().add(detalle);
            totalAcumulado += detalle.getSubtotal();
        }
        // We round the total ONCE here. This ensures the Venta Header has a clean
        // value.
        Double totalRounded = Math.round(totalAcumulado * 100.0) / 100.0;
        result.setTotalVenta(totalRounded);

        return result;
    }

    /**
     * Determines the descripcion parameter for sibling/WAC queries.
     * Generic products (codigo='1') use both codigo AND descripcion as family key
     * to avoid blending unrelated items (e.g. Apples vs Shoes, both coded '1').
     */
    private String resolveFamilyDescripcion(Product producto) {
        return "1".equals(producto.getCodigo()) ? producto.getDescripcion() : null;
    }

    /**
     * Creates a DetalleVenta object with all price calculations applied.
     * Cost snapshot uses Weighted Average Cost (WAC) of the product family to
     * accurately represent the blended cost-of-goods-sold across all variants.
     */
    private DetalleVenta createDetalleVenta(Product producto, VentaRequest.ItemRequest itemReq, TipoVenta tipoVenta) {
        DetalleVenta detalle = new DetalleVenta();

        // A. Traceability: Save the original product the cashier rang up.
        detalle.setProductoId(producto.getId());
        detalle.setCodigoSnapshot(producto.getCodigo());
        detalle.setDescripcionSnapshot(producto.getDescripcion());

        // B. Compute WAC for economic snapshot.
        //    WAC uses GREATEST(0, stock) clamping to prevent phantom-sold inventory
        //    (negative stock variants) from deflating the blended average cost.
        //    Falls back to the newest sibling's precio_costo when all stock is zero or negative.
        //    NOTE: To switch to FIFO instead of WAC, replace this block with:
        //      1) Remove the findWAC() call.
        //      2) Sort siblings by id ASC (oldest first — findSiblingsByFamily already does this).
        //      3) Deduct quantity sequentially from each sibling's stock and weight its cost,
        //         iterating until quantityNeeded is satisfied.
        //      The CostCalculationStrategy interface can be introduced here to swap strategies
        //      at runtime via Spring dependency injection if needed in the future.
        String familyDescripcion = resolveFamilyDescripcion(producto);
        java.util.Optional<Double> wacOptional = productRepository.findWAC(producto.getCodigo(), familyDescripcion);

        // If WAC is null (e.g., zero total stock in the system), fallback to the cost
        // of the explicitly selected variant that the cashier rang up.
        double wac = wacOptional.orElse(producto.getPrecioCosto());

        // Round to 2 decimal places to prevent floating-point drift in accounting reports
        detalle.setCostoSnapshot(Math.round(wac * 100.0) / 100.0);
        detalle.setCantidad(itemReq.getCantidad());

        // C. CALCULATE PRICES
        // ---------------------------------------------------------
        // Select Price based on Sale Type (Default to Retail if null)
        Double precioBase;
        if (tipoVenta == TipoVenta.MAYORISTA) {
            precioBase = producto.getPrecioMayorista();
            // Fallback: If wholesale price is missing/zero, should we use retail?
            // For now, let's assume if it's 0 it's 0 (maybe a gift or unconfigured).
            // Business Rule: "Wholesale Price >= 0 (if present)".
            if (precioBase == null)
                precioBase = 0.0;
        } else {
            precioBase = producto.getPrecioMinorista();
        }

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
            throw new BusinessRuleException("El monto de descuento ($" + value
                    + ") no puede ser mayor al precio del producto ($" + basePrice + ") para: " + productName);
        }

        Double finalPrice = basePrice - value;

        // Round to 2 decimals to avoid floating point errors (e.g. 99.999999)
        return Math.round(finalPrice * 100.0) / 100.0;
    }

    private PersistedTransactionInfo saveTransactionData(VentaRequest request, ProcessedSaleResult processedData) {
        PersistedTransactionInfo info = new PersistedTransactionInfo();

        // A. Header
        Venta venta = new Venta();
        venta.setFecha(LocalDateTime.now(ZoneId.systemDefault())); // Result: LocalDateTime
        venta.setClienteNombre(request.getClienteNombre());
        venta.setTotalVenta(processedData.getTotalVenta());
        venta.setDescuentoGlobal(processedData.getDescuentoGlobal());
        venta.setTipoVenta(request.getTipoVenta() != null ? request.getTipoVenta().name() : "MINORISTA"); // Default

        // If the frontend sends null, this will be null in DB (allowed by schema if not
        // strict, but good for traceability)
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
    // PACKAGE-PRIVATE (Visible to Tests, Hidden from Controller and possible
    // subclasses)
    List<String> updateStockFromDetails(List<DetalleVenta> detalles) {
        List<String> alerts = new ArrayList<>();

        for (DetalleVenta detalle : detalles) {
            // D. Deduct Stock
            // We use detail.getDescripcionSnapshot() as the product name for alerts
            String alerta = deductStockFromInventory(detalle.getProductoId(), detalle.getDescripcionSnapshot(),
                    detalle.getCantidad());

            if (alerta != null) {
                alerts.add(alerta);
            }
        }
        return alerts;
    }

    /**
     * ATOMIC DECREMENT LOGIC
     * Manages stock deduction across multiple locations.
     * If stock is not enough, it forces a negative balance on the first available
     * location.
     */
    // PACKAGE-PRIVATE (Visible to Tests, Hidden from Controller and possible
    // subclasses)
    String deductStockFromInventory(Long productId, String productName, Long quantityNeeded) {
        List<StockLocation> locations = stockRepository.findByProductId(productId);
        Long remainingToDeduct = quantityNeeded;

        for (StockLocation loc : locations) {
            if (remainingToDeduct <= 0)
                break;
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
                // Scenario: Product exists in DB but has NO entry in 'stock_por_ubicacion'.
                // FIX: Auto-create a negative stock row on the first available system location
                // so the sale is always fully persisted. If no locations exist at all in the
                // system, escalate to a CRÍTICO warning requiring admin intervention.
                List<com.centralizesys.model.product.Location> allLocations = stockRepository.findAllLocations();
                if (allLocations.isEmpty()) {
                    return "CRÍTICO: El producto '" + productName
                            + "' se vendió pero el sistema no tiene NINGUNA ubicación de stock configurada. "
                            + "Contacte al administrador del sistema.";
                }
                Long defaultLocId = allLocations.getFirst().getId();
                stockRepository.addStock(productId, defaultLocId, -remainingToDeduct);
                return "ATENCIÓN: El producto '" + productName
                        + "' se vendió pero no tenía ubicación asignada. "
                        + "El sistema registró stock negativo en la ubicación primaria del sistema.";
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
        // this check safely filters out false positives without needing to round
        // 'totalPagado'.
        Double epsilon = 0.0001;

        if (deuda > epsilon) {
            if (clienteNombre == null || clienteNombre.isBlank()) {
                throw new BusinessRuleException("Para dejar una deuda (Fiado), se requiere el nombre del cliente.");
            }

            // 4. Final Rounding
            // We only round here to ensure the database gets a clean "5.50" instead of
            // "5.499999"
            double deudaFinal = Math.round(deuda * 100.0) / 100.0;

            deudoresRepository.save(ventaId, clienteNombre, deudaFinal);
        }
    }

    @Transactional
    public void anularVentaHistorica(Long ventaId) {
        // 1. Fetch Sale
        Venta venta = ventaRepository.findById(ventaId)
                .orElseThrow(() -> new ResourceNotFoundException("Venta", ventaId));

        if (ANULADA.equals(venta.getEstado())) {
            throw new BusinessRuleException("La venta ya se encuentra anulada.");
        }

        // 2. Mark Sale as ANULADA
        ventaRepository.updateEstado(ventaId, ANULADA);

        // 3. Return Stock to Primary Location
        List<DetalleVenta> detalles = ventaRepository.findDetallesByVentaId(ventaId);
        if (!detalles.isEmpty()) {
            List<com.centralizesys.model.product.Location> allLocations = stockRepository.findAllLocations();
            if (allLocations.isEmpty()) {
                throw new BusinessRuleException("No hay ubicaciones configuradas para retornar el stock.");
            }
            Long primaryLocationId = allLocations.getFirst().getId();

            for (DetalleVenta detalle : detalles) {
                // Ignore negative quantities (returns?) or just add back whatever was sold
                stockRepository.addStock(detalle.getProductoId(), primaryLocationId, detalle.getCantidad());
            }
        }

        // 4. Void associated Debt (if any)
        deudoresRepository.findByVentaId(ventaId)
                .ifPresent(deuda ->
                    // Force state to ANULADA
                    deudoresRepository.updateMontoAndEstado(deuda.getId(), deuda.getMontoDeuda(), ANULADA)
                );

        // 5. Audit Log
        Long currentUserId = com.centralizesys.security.SecurityUtils.getAuthenticatedUserId();
        auditoriaService.registrarAccion(
                currentUserId,
                "ANULAR_VENTA",
                "Se anuló la venta ID " + ventaId + " y se retornó el stock a la ubicación principal.");
    }
}