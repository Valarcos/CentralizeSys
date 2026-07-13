package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.debt.PagoDeudaRequest;
import com.centralizesys.model.sales.DetalleVenta;
import com.centralizesys.model.sales.PagoVenta;
import com.centralizesys.model.sales.Venta;
import com.centralizesys.model.sales.VentaRequest;
import com.centralizesys.model.sales.VentaResponse;
import com.centralizesys.repository.DeudoresRepository;
import com.centralizesys.repository.PendingSaleRepository;
import com.centralizesys.repository.StockRepository;
import com.centralizesys.repository.VentaRepository;
import com.centralizesys.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

@Service
public class PendingSaleService {

    private static final Logger log = LoggerFactory.getLogger(PendingSaleService.class);

    private final PendingSaleRepository pendingSaleRepository;
    private final VentaService ventaService;
    private final StockRepository stockRepository;
    private final VentaRepository ventaRepository;
    private final DeudoresRepository deudoresRepository;
    private final AuditoriaService auditoriaService;

    private static final String VENTA_PENDIENTE = "Venta Pendiente";
    private static final String PENDIENTE = "PENDIENTE";

    // Debt detection margin: balance under this threshold is considered fully paid
    private static final double PAYMENT_COMPLETE_EPSILON = 0.01;

    public PendingSaleService(PendingSaleRepository pendingSaleRepository,
                              VentaService ventaService,
                              StockRepository stockRepository,
                              VentaRepository ventaRepository,
                              DeudoresRepository deudoresRepository,
                              AuditoriaService auditoriaService) {
        this.pendingSaleRepository = pendingSaleRepository;
        this.ventaService = ventaService;
        this.stockRepository = stockRepository;
        this.ventaRepository = ventaRepository;
        this.deudoresRepository = deudoresRepository;
        this.auditoriaService = auditoriaService;
    }

    @Transactional
    public Long crearPendiente(VentaRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessRuleException("La venta pendiente debe tener al menos un producto.");
        }

        // 1. Process Items (reuses VentaService logic for price/discounts)
        VentaService.ProcessedSaleResult processedData = ventaService.processItems(request.getItems(), request.getTipoVenta());

        Double subtotal = processedData.getTotalVenta();
        Double descuentoGlobal = request.getDescuentoGlobal() != null ? request.getDescuentoGlobal() : 0.0;
        if (descuentoGlobal < 0 || descuentoGlobal > subtotal) {
            throw new BusinessRuleException("Descuento global inválido.");
        }
        Double finalTotal = Math.round((subtotal - descuentoGlobal) * 100.0) / 100.0;

        // 2. Save Pending Header
        Venta pendingSale = new Venta();
        pendingSale.setFecha(LocalDateTime.now(ZoneId.systemDefault()));
        pendingSale.setClienteNombre(request.getClienteNombre());
        pendingSale.setTotalVenta(finalTotal); // Stored in total_estimado
        pendingSale.setDescuentoGlobal(descuentoGlobal);
        pendingSale.setTipoVenta(request.getTipoVenta() != null ? request.getTipoVenta().name() : "MINORISTA");
        pendingSale.setUsuarioId(request.getUsuarioId());
        pendingSale.setEstado(PENDIENTE);

        Long pendingId = pendingSaleRepository.savePendiente(pendingSale);

        // 3. Save Pending Details
        List<DetalleVenta> detalles = processedData.getDetalles();
        detalles.forEach(d -> d.setVentaId(pendingId)); // Sets venta_pendiente_id
        pendingSaleRepository.saveDetalles(detalles);

        // 4. Reserve Stock (Physical deduction)
        ventaService.updateStockFromDetails(detalles);

        // 5. Audit
        auditoriaService.registrarAccion(request.getUsuarioId(), "CREAR_PENDIENTE", "Pedido creado con ID: " + pendingId);

        return pendingId;
    }

    /**
     * Registers one or more partial deposit payments against a pending sale.
     *
     * Business rules enforced:
     *  - The sale must be in PENDIENTE state (not FINALIZADA or CANCELADA).
     *  - Total payment must be > 0.
     *  - Payment must not exceed the remaining balance (total_estimado - monto_pagado).
     *
     * @param id       The ID of the pending sale (ventas_pendientes.id).
     * @param pagos    List of payment splits (method + amount).
     * @param usuarioId The authenticated user recording the payment.
     */
    @Transactional
    public void registrarPago(Long id, List<PagoDeudaRequest> pagos, Long usuarioId) {
        if (pagos == null || pagos.isEmpty()) {
            throw new BusinessRuleException(Constants.ERR_PAYMENT_NEGATIVE);
        }

        // 1. Calculate total payment amount
        double totalNuevoPago = pagos.stream()
                .mapToDouble(PagoDeudaRequest::getMontoPago)
                .sum();

        if (totalNuevoPago <= 0) {
            throw new BusinessRuleException(Constants.ERR_PAYMENT_NEGATIVE);
        }

        // 2. Fetch the pending sale (guard: must exist and be in PENDIENTE state)
        Venta pendingSale = pendingSaleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(Constants.ERR_PENDING_SALE_NOT_FOUND, id));

        if (!PENDIENTE.equals(pendingSale.getEstado())) {
            throw new BusinessRuleException("Solo se pueden registrar pagos en pedidos con estado PENDIENTE.");
        }

        // 3. Balance check: prevent overpayment
        Double totalPagadoPrevio = pendingSaleRepository.sumPagosActivosByVentaPendienteId(id);
        double saldoRestante = Math.round((pendingSale.getTotalVenta() - totalPagadoPrevio) * 100.0) / 100.0;

        double totalNuevoPagoRounded = Math.round(totalNuevoPago * 100.0) / 100.0;
        if (totalNuevoPagoRounded > saldoRestante + PAYMENT_COMPLETE_EPSILON) {
            log.warn("Intento de pago que excede saldo. Pedido ID: {}. Saldo: {}. Pago intentado: {}.",
                    id, saldoRestante, totalNuevoPagoRounded);
            throw new BusinessRuleException(Constants.ERR_PENDING_PAYMENT_EXCEEDS_BALANCE);
        }

        // 4. Persist each payment split (each method+amount gets its own row)
        for (PagoDeudaRequest pago : pagos) {
            if (pago.getMontoPago() != null && pago.getMontoPago() > 0) {
                pendingSaleRepository.savePagoVentaPendiente(
                        id, pago.getMetodoPagoId(), pago.getMontoPago(), usuarioId);
            }
        }

        // 5. Audit
        auditoriaService.registrarAccion(usuarioId, "PAGO_PENDIENTE",
                String.format("Registrado pago de $%.2f en Pedido ID %d.", totalNuevoPago, id));
    }

    @Transactional
    public VentaResponse modificarCarrito(Long id, VentaRequest request, Long usuarioId) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessRuleException("El carrito no puede estar vacío.");
        }

        Venta pendingSale = pendingSaleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(VENTA_PENDIENTE, id));

        if (!PENDIENTE.equals(pendingSale.getEstado())) {
            throw new BusinessRuleException("Solo se puede modificar un pedido en estado PENDIENTE.");
        }

        com.centralizesys.model.sales.TipoVenta tipoVenta = request.getTipoVenta() != null
                ? request.getTipoVenta()
                : com.centralizesys.model.sales.TipoVenta.valueOf(pendingSale.getTipoVenta());

        VentaService.ProcessedSaleResult processedData = ventaService.processItems(request.getItems(), tipoVenta);

        Double subtotal = processedData.getTotalVenta();
        Double descuentoGlobal = request.getDescuentoGlobal() != null ? request.getDescuentoGlobal() : 0.0;
        if (descuentoGlobal < 0 || descuentoGlobal > subtotal) {
            throw new BusinessRuleException("Descuento global inválido.");
        }
        Double finalTotal = Math.round((subtotal - descuentoGlobal) * 100.0) / 100.0;

        Double totalPagado = pendingSaleRepository.sumPagosActivosByVentaPendienteId(id);
        if (finalTotal < totalPagado) {
            throw new BusinessRuleException(String.format("El nuevo total ($%.2f) no puede ser menor al monto ya pagado ($%.2f).", finalTotal, totalPagado));
        }

        // Return old stock
        List<DetalleVenta> oldDetails = pendingSaleRepository.findDetallesByVentaPendienteId(id);
        if (!oldDetails.isEmpty()) {
            List<com.centralizesys.model.product.Location> allLocations = stockRepository.findAllLocations();
            if (!allLocations.isEmpty()) {
                Long primaryLocId = allLocations.getFirst().getId();
                for (DetalleVenta d : oldDetails) {
                    stockRepository.addStock(d.getProductoId(), primaryLocId, d.getCantidad());
                }
            }
        }

        // Mark old as annulled
        pendingSaleRepository.marcarDetallesComoAnulados(id);

        // Save new items
        List<DetalleVenta> detalles = processedData.getDetalles();
        detalles.forEach(d -> d.setVentaId(id));
        pendingSaleRepository.saveDetalles(detalles);

        // Deduct new stock
        List<String> stockAlerts = ventaService.updateStockFromDetails(detalles);

        // Update Totals
        pendingSaleRepository.updateTotalesConOCC(id, finalTotal, descuentoGlobal);

        // Audit
        auditoriaService.registrarAccion(usuarioId, "MODIFICAR_CARRITO_PENDIENTE", "Pedido ID: " + id + ". Nuevo Total: $" + finalTotal);

        List<PagoVenta> pagosActivos = pendingSaleRepository.findPagosActivosByVentaPendienteId(id);

        return new VentaResponse(
                id,
                pendingSale.getFecha(),
                request.getClienteNombre(),
                ventaRepository.findVendedorNombre(pendingSale.getUsuarioId()),
                finalTotal,
                descuentoGlobal,
                pendingSale.getTipoVenta(),
                detalles,
                pagosActivos,
                stockAlerts,
                PENDIENTE,
                pendingSale.getCostoTotal() != null ? pendingSale.getCostoTotal() : 0.0
        );
    }

    @Transactional
    public void anularPago(Long pendingId, Long pagoId, Long usuarioId) {
        Venta pendingSale = pendingSaleRepository.findById(pendingId)
                .orElseThrow(() -> new ResourceNotFoundException(VENTA_PENDIENTE, pendingId));

        if (!PENDIENTE.equals(pendingSale.getEstado())) {
            throw new BusinessRuleException("Solo se pueden anular pagos de pedidos en estado PENDIENTE.");
        }

        Double monto = pendingSaleRepository.getMontoPagoActivo(pagoId, pendingId);

        pendingSaleRepository.updatePagoAnulado(pagoId);
        pendingSaleRepository.decrementMontoPagado(pendingId, monto);

        auditoriaService.registrarAccion(usuarioId, "ANULAR_PAGO_PENDIENTE", "Pago ID: " + pagoId + " del Pedido ID: " + pendingId + " por $" + monto + " anulado.");
    }

    @Transactional
    public void cancelarPendiente(Long id) {
        // 1. Fetch pending sale
        Venta pendingSale = pendingSaleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(VENTA_PENDIENTE, id));

        if (!PENDIENTE.equals(pendingSale.getEstado())) {
            throw new BusinessRuleException("Solo se pueden cancelar pedidos en estado PENDIENTE.");
        }

        // 2. Update state to CANCELADA
        pendingSaleRepository.updateEstado(id, "CANCELADA");

        // 3. Return stock
        List<DetalleVenta> detalles = pendingSaleRepository.findDetallesByVentaPendienteId(id);
        if (!detalles.isEmpty()) {
            List<com.centralizesys.model.product.Location> allLocations = stockRepository.findAllLocations();
            if (allLocations.isEmpty()) {
                throw new BusinessRuleException("No hay ubicaciones para retornar el stock reservado.");
            }
            Long primaryLocId = allLocations.getFirst().getId();

            for (DetalleVenta d : detalles) {
                stockRepository.addStock(d.getProductoId(), primaryLocId, d.getCantidad());
            }
        }

        auditoriaService.registrarAccion(0L, "CANCELAR_PENDIENTE", "Pedido ID " + id + " cancelado.");
    }

    /**
     * Finalizes a pending sale by migrating it to a fully-fledged Venta.
     *
     * Enhanced Business Rules (Phase 5):
     *  1. The pending sale must have at least one active payment (monto_pagado > 0).
     *     This is a defense-in-depth guard; the UI also blocks the button when monto_pagado = 0.
     *  2. All active payments in pagos_venta_pendiente are migrated to pagos_venta.
     *  3. If total paid < total_estimado, the remaining balance auto-generates a debt
     *     in the 'deudores' table (Debt Conversion / Fiado auto-creation).
     *  4. Stock remains deducted (it was reserved on creation). No further stock operation.
     *
     * @param id The ID of the pending sale to finalize.
     * @return The full VentaResponse of the newly created Venta.
     */
    @Transactional
    public VentaResponse finalizarVenta(Long id) {
        // 1. Fetch pending sale
        Venta pendingSale = pendingSaleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(VENTA_PENDIENTE, id));

        if (!PENDIENTE.equals(pendingSale.getEstado())) {
            throw new BusinessRuleException("Solo se pueden finalizar pedidos en estado PENDIENTE.");
        }

        // 2. Enforce: at least one payment must have been registered (defense-in-depth)
        List<PagoVenta> pagosActivos = pendingSaleRepository.findPagosActivosByVentaPendienteId(id);
        double totalPagado = pagosActivos.stream().mapToDouble(PagoVenta::getMonto).sum();
        totalPagado = Math.round(totalPagado * 100.0) / 100.0;

        if (totalPagado <= PAYMENT_COMPLETE_EPSILON) {
            throw new BusinessRuleException(Constants.ERR_PENDING_FINALIZE_NO_PAYMENT);
        }

        // 3. Migrate Header — creates a new Venta with the frozen price from the pending sale
        Venta finalSale = new Venta();
        finalSale.setFecha(LocalDateTime.now(ZoneId.systemDefault())); // New timestamp for actual sale
        finalSale.setClienteNombre(pendingSale.getClienteNombre());
        finalSale.setTotalVenta(pendingSale.getTotalVenta()); // Frozen Total!
        finalSale.setDescuentoGlobal(pendingSale.getDescuentoGlobal());
        finalSale.setTipoVenta(pendingSale.getTipoVenta());
        finalSale.setUsuarioId(pendingSale.getUsuarioId());
        // 'estado' will default to ACTIVA through saveVenta mapped entity

        Long newVentaId = ventaRepository.saveVenta(finalSale);

        // 4. Migrate Details
        List<DetalleVenta> detalles = pendingSaleRepository.findDetallesByVentaPendienteId(id);
        detalles.forEach(d -> d.setVentaId(newVentaId));
        ventaRepository.saveDetalles(detalles);

        // 5. Migrate Payments — re-link all active pagos_venta_pendiente to the new Venta
        List<PagoVenta> migratedPagos = pagosActivos.stream()
                .map(p -> new PagoVenta(null, newVentaId, p.getMetodoPagoId(), p.getMonto()))
                .toList();
        ventaRepository.savePagos(migratedPagos);

        // 6. Debt Conversion — if sale is only partially paid, generate a Fiado
        double totalEstimado = pendingSale.getTotalVenta();
        double saldoPendiente = Math.round((totalEstimado - totalPagado) * 100.0) / 100.0;

        if (saldoPendiente > PAYMENT_COMPLETE_EPSILON) {
            log.info("Pedido ID {} finalizado con deuda residual de ${}. Generando Fiado automático.", id, saldoPendiente);
            deudoresRepository.save(newVentaId, pendingSale.getClienteNombre(), saldoPendiente);
        }

        // 7. Update Pending status to FINALIZADA
        pendingSaleRepository.updateEstado(id, "FINALIZADA");

        // Stock was already reserved when the pending sale was created — NO stock deduction here!

        auditoriaService.registrarAccion(pendingSale.getUsuarioId(), "FINALIZAR_PENDIENTE",
                String.format("Pedido %d finalizado como Venta %d. Pagado: $%.2f. Deuda: $%.2f.",
                        id, newVentaId, totalPagado, saldoPendiente));

        String vendedorNombre = ventaRepository.findVendedorNombre(pendingSale.getUsuarioId());
        return new VentaResponse(
                newVentaId,
                finalSale.getFecha(),
                finalSale.getClienteNombre(),
                vendedorNombre,
                finalSale.getTotalVenta(),
                finalSale.getDescuentoGlobal(),
                finalSale.getTipoVenta(),
                detalles,
                migratedPagos,
                Collections.emptyList(), // No stock alerts on finalization (stock was reserved at creation)
                "ACTIVA",
                pendingSale.getCostoTotal() != null ? pendingSale.getCostoTotal() : 0.0
        );
    }


}
