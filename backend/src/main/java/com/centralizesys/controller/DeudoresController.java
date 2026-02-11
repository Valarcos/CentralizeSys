package com.centralizesys.controller;

import com.centralizesys.model.debt.DeudaResponse;
import com.centralizesys.model.debt.PagoDeudaRequest;
import com.centralizesys.service.DeudoresService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deudores")
public class DeudoresController {

    private final DeudoresService service;

    public DeudoresController(DeudoresService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<DeudaResponse>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @PostMapping("/{id}/pagar")
    public ResponseEntity<DeudaResponse> pagarDeuda(
            @PathVariable Long id,
            @RequestBody List<PagoDeudaRequest> pagos) {

        // Use SecurityContext to identify user
        Long usuarioId = com.centralizesys.security.SecurityUtils.getAuthenticatedUserId();

        // Pass the extracted ID to the Service
        DeudaResponse updated = service.registrarPago(
                id,
                pagos,
                usuarioId);

        return ResponseEntity.ok(updated);
    }

    // GET /api/deudores/reminder
    // Returns true if active debts exist (for 15-day reminder badge)
    @GetMapping("/reminder")
    public ResponseEntity<Boolean> hasActiveDebtsReminder() {
        return ResponseEntity.ok(service.hasActiveDebts());
    }

    @GetMapping("/expired")
    public ResponseEntity<List<DeudaResponse>> getExpiredDebts() {
        return ResponseEntity.ok(service.getExpiredDebts());
    }
}