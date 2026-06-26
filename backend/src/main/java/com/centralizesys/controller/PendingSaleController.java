package com.centralizesys.controller;

import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.debt.PagoDeudaRequest;
import com.centralizesys.model.sales.DetalleVenta;
import com.centralizesys.model.sales.Venta;
import com.centralizesys.model.sales.VentaRequest;
import com.centralizesys.model.sales.VentaResponse;
import com.centralizesys.repository.PendingSaleRepository;
import com.centralizesys.repository.VentaRepository;
import com.centralizesys.security.SecurityUtils;
import com.centralizesys.service.PendingSaleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/ventas-pendientes")
public class PendingSaleController {

    private final PendingSaleService pendingSaleService;
    private final PendingSaleRepository pendingSaleRepository;
    private final VentaRepository ventaRepository;

    public PendingSaleController(PendingSaleService pendingSaleService,
                                 PendingSaleRepository pendingSaleRepository,
                                 VentaRepository ventaRepository) {
        this.pendingSaleService = pendingSaleService;
        this.pendingSaleRepository = pendingSaleRepository;
        this.ventaRepository = ventaRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<VentaResponse> getPendingSaleById(@PathVariable Long id) {
        Venta sale = pendingSaleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venta Pendiente", id));

        List<DetalleVenta> items = pendingSaleRepository.findDetallesByVentaPendienteId(id);
        String vendedorNombre = ventaRepository.findVendedorNombre(sale.getUsuarioId());

        VentaResponse response = new VentaResponse(
                sale.getId(),
                sale.getFecha(),
                sale.getClienteNombre(),
                vendedorNombre,
                sale.getTotalVenta(),
                sale.getDescuentoGlobal(),
                sale.getTipoVenta(),
                items,
                Collections.emptyList(),
                Collections.emptyList(),
                sale.getEstado()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<Long> crearPendiente(@RequestBody VentaRequest request) {
        Long id = pendingSaleService.crearPendiente(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    /**
     * Registers one or more deposit payments against an existing pending sale.
     *
     * Zero-Trust: The user ID for auditing is extracted from the validated JWT via
     * SecurityUtils.getAuthenticatedUserId(). Any user ID in the request body is ignored.
     *
     * Double-Submit protection must be enforced on the frontend button.
     */
    @PostMapping("/{id}/pagos")
    public ResponseEntity<Void> registrarPago(
            @PathVariable Long id,
            @RequestBody List<PagoDeudaRequest> pagos) {

        // Zero-Trust: extract identity from validated JWT, never from client payload
        Long authenticatedUserId = SecurityUtils.getAuthenticatedUserId();
        pendingSaleService.registrarPago(id, pagos, authenticatedUserId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Retrieves all active (non-voided) payments for a pending sale.
     * Used by the frontend to display the payment history and calculate remaining balance.
     */
    @GetMapping("/{id}/pagos")
    public ResponseEntity<List<com.centralizesys.model.sales.PagoVenta>> getPagos(@PathVariable Long id) {
        List<com.centralizesys.model.sales.PagoVenta> pagos =
                pendingSaleRepository.findPagosActivosByVentaPendienteId(id);
        return ResponseEntity.ok(pagos);
    }

    @PostMapping("/{id}/finalizar")
    public ResponseEntity<VentaResponse> finalizarVenta(@PathVariable Long id) {
        VentaResponse response = pendingSaleService.finalizarVenta(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<Void> cancelarPendiente(@PathVariable Long id) {
        pendingSaleService.cancelarPendiente(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<VentaResponse> modificarCarrito(
            @PathVariable Long id,
            @RequestBody VentaRequest request) {
        Long authenticatedUserId = SecurityUtils.getAuthenticatedUserId();
        VentaResponse response = pendingSaleService.modificarCarrito(id, request, authenticatedUserId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{pendingId}/pagos/{pagoId}")
    public ResponseEntity<Void> anularPago(
            @PathVariable Long pendingId,
            @PathVariable Long pagoId) {
        Long authenticatedUserId = SecurityUtils.getAuthenticatedUserId();
        pendingSaleService.anularPago(pendingId, pagoId, authenticatedUserId);
        return ResponseEntity.noContent().build();
    }
}
