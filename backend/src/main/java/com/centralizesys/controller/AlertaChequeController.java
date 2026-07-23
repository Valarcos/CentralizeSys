package com.centralizesys.controller;

import com.centralizesys.model.cheque.AlertaCheque;
import com.centralizesys.repository.AlertaChequeRepository;
import com.centralizesys.service.VentaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alertas")
public class AlertaChequeController {

    private final AlertaChequeRepository alertaChequeRepository;
    private final VentaService ventaService;

    public AlertaChequeController(AlertaChequeRepository alertaChequeRepository, VentaService ventaService) {
        this.alertaChequeRepository = alertaChequeRepository;
        this.ventaService = ventaService;
    }

    @GetMapping("/cheques")
    public ResponseEntity<List<AlertaCheque>> getPendingCheques() {
        List<AlertaCheque> alertas = alertaChequeRepository.findPendingExpiredOrToday();
        return ResponseEntity.ok(alertas);
    }

    @PostMapping("/cheques/{id}/cobrar")
    public ResponseEntity<Void> cobrarCheque(@PathVariable Long id, @RequestParam Long metodoPagoId) {
        Long currentUserId = com.centralizesys.security.SecurityUtils.getAuthenticatedUserId();
        ventaService.cobrarCheque(id, metodoPagoId, currentUserId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/cheques/{id}/cancelar-cobro")
    public ResponseEntity<Void> cancelarCobroCheque(@PathVariable Long id) {
        Long currentUserId = com.centralizesys.security.SecurityUtils.getAuthenticatedUserId();
        ventaService.cancelarCobroCheque(id, currentUserId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/cheques/{id}")
    public ResponseEntity<Void> anularCheque(@PathVariable Long id) {
        Long currentUserId = com.centralizesys.security.SecurityUtils.getAuthenticatedUserId();
        ventaService.anularCheque(id, currentUserId);
        return ResponseEntity.ok().build();
    }

    /**
     * Returns all alertas_cheques for a given venta, across all states (PENDIENTE, COBRADO, ANULADA).
     * Used by the "CobrarChequesModal" to display the full installment history for a sale.
     */
    @GetMapping("/cheques/venta/{ventaId}")
    public ResponseEntity<List<AlertaCheque>> getByVentaId(@PathVariable Long ventaId) {
        List<AlertaCheque> alertas = alertaChequeRepository.findByVentaId(ventaId);
        return ResponseEntity.ok(alertas);
    }
}
