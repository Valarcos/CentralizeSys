package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.enums.DiscountType;
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

    public VentaService(VentaRepository ventaRepository,
                        ProductRepository productRepository,
                        StockRepository stockRepository,
                        DeudoresRepository deudoresRepository) {
        this.ventaRepository = ventaRepository;
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.deudoresRepository = deudoresRepository;
    }

    /**
     * Orchestrates the sales process.
     * High-level manager calling specialized steps.
     */
    @Transactional
    public VentaResponse registrarVenta(VentaRequest request) {
        // 1. Validate
        validateRequest(request);

        // 2. Process Items (Calculate Totals, Stock Snapshots)
        ProcessedSaleResult processedData = processItemsAndStock(request.getItems());

        // 3. Persist Data (Header, Details, Payments)
        // Returns both ID and the processed payments list
        PersistedTransactionInfo txInfo = saveTransactionData(request, processedData);

        // 4. Handle Debt (Fiados)
        handleDebt(txInfo.getVentaId(), request.getClienteNombre(), processedData.getTotalVenta(), txInfo.getPagosPersistidos());

        // 5. Build Response
        return new VentaResponse(
                txInfo.getVentaId(),
                LocalDate.now().toString(),
                request.getClienteNombre(),
                processedData.getTotalVenta(),
                processedData.getDetalles(),
                txInfo.getPagosPersistidos(),
                processedData.getAlertas()
        );
    }

    // --- HELPER CLASSES (Internal DTOs) ---

    @Data
    private static class ProcessedSaleResult {
        private List<DetalleVenta> detalles;
        private List<String> alertas;
        private Double totalVenta;
    }

    @Data
    private static class PersistedTransactionInfo {
        private Long ventaId;
        private List<PagoVenta> pagosPersistidos;
    }

    // --- HELPER METHODS ---

    private void validateRequest(VentaRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessRuleException("La venta debe tener al menos un producto.");
        }
    }

    // TODO: This is a borderline GOD method, consider partitioning into helper methods should it become bigger
    private ProcessedSaleResult processItemsAndStock(List<VentaRequest.ItemRequest> itemsReq) {
        ProcessedSaleResult result = new ProcessedSaleResult();
        result.setDetalles(new ArrayList<>());
        result.setAlertas(new ArrayList<>());
        Double totalAcumulado = 0.0;

        for (VentaRequest.ItemRequest itemReq : itemsReq) {
            // A. Fetch Product
            Product producto = productRepository.findById(itemReq.getProductoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto", itemReq.getProductoId()));

            // B. Build Detail (Snapshot Base Info)
            DetalleVenta detalle = new DetalleVenta();
            detalle.setProductoId(producto.getId());
            detalle.setCodigoSnapshot(producto.getCodigo());
            detalle.setDescripcionSnapshot(producto.getDescripcion());
            detalle.setCantidad(itemReq.getCantidad());

            // C. CALCULATE PRICES (The New Business Rule)
            // ---------------------------------------------------------
            Double precioBase = producto.getPrecioMinorista(); // Official DB Price
            detalle.setPrecioLista(precioBase);

            DiscountType type = itemReq.getTipoDescuento() != null ? itemReq.getTipoDescuento() : DiscountType.NONE;
            Double valorDescuento = itemReq.getValorDescuento() != null ? itemReq.getValorDescuento() : 0.0;

            // Logic to calculate final price
            Double precioFinal = calculateFinalPrice(precioBase, type, valorDescuento, producto.getDescripcion());

            detalle.setDescuentoTipo(type);
            detalle.setDescuentoValor(valorDescuento);
            detalle.setPrecioUnitario(precioFinal);
            // ---------------------------------------------------------

            Double subtotal = itemReq.getCantidad() * precioFinal;
            detalle.setSubtotal(subtotal);

            result.getDetalles().add(detalle);
            totalAcumulado += subtotal;

            // D. Deduct Stock
            String alerta = deductStockFromInventory(producto.getId(), producto.getDescripcion(), itemReq.getCantidad());
            if (alerta != null) {
                result.getAlertas().add(alerta);
            }
        }

        result.setTotalVenta(totalAcumulado);
        return result;
    }

    /**
     * Helper to handle the math and validation of discounts
     */
    private Double calculateFinalPrice(Double basePrice, DiscountType type, Double value, String productName) {
        if (value < 0) {
            throw new BusinessRuleException("El descuento no puede ser negativo para: " + productName);
        }

        Double finalPrice = switch (type) {
            case PERCENTAGE -> {
                if (value > 100) {
                    throw new BusinessRuleException("El porcentaje de descuento no puede superar 100% para: " + productName);
                }
                yield basePrice - (basePrice * (value / 100.0));
            }
            case FIXED -> {
                if (value > basePrice) {
                    throw new BusinessRuleException("El monto de descuento ($" + value + ") no puede ser mayor al precio del producto ($" + basePrice + ") para: " + productName);
                }
                yield basePrice - value;
            }
            default -> basePrice;
        };

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

    private void handleDebt(Long ventaId, String clienteNombre, Double totalVenta, List<PagoVenta> pagosPersistidos) {
        Double totalPagado = pagosPersistidos.stream()
                .mapToDouble(PagoVenta::getMonto)
                .sum();

        // Precision check (allow 0.01 difference)
        if (totalPagado < totalVenta - 0.01) {
            Double deuda = totalVenta - totalPagado;
            if (clienteNombre == null || clienteNombre.isBlank()) {
                throw new BusinessRuleException("Para dejar una deuda (Fiado), se requiere el nombre del cliente.");
            }
            deudoresRepository.save(ventaId, clienteNombre, deuda);
        }
    }

    /**
     * ATOMIC DECREMENT LOGIC
     */
    private String deductStockFromInventory(Long productId, String productName, Long quantityNeeded) {
        List<StockLocation> locations = stockRepository.findByProductId(productId);
        Long remainingToDeduct = quantityNeeded;

        for (StockLocation loc : locations) {
            if (remainingToDeduct <= 0) break;
            Long available = loc.getCantidad();
            if (available > 0) {
                Long toTake = Math.min(available, remainingToDeduct);
                stockRepository.subtractStock(loc.getLocationId(), productId, toTake);
                remainingToDeduct -= toTake;
            }
        }

        if (remainingToDeduct > 0) {
            if (locations.isEmpty()) {
                return "CRÍTICO: El producto '" + productName + "' se vendió pero NO tiene ubicación de stock asignada.";
            } else {
                Long defaultLocId = locations.getFirst().getLocationId();
                stockRepository.subtractStock(defaultLocId, productId, remainingToDeduct);
                return "ATENCIÓN: Stock insuficiente para '" + productName + "'. El sistema registró stock negativo.";
            }
        }
        return null;
    }
}