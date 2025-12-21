package com.centralizesys.controller;

import com.centralizesys.model.debt.DeudaResponse;
import com.centralizesys.model.debt.PagoDeudaRequest;
import com.centralizesys.service.DeudoresService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// TODO: replace the CORS codesmell with the proper URLs
@RestController
@RequestMapping("/api/deudores")
@CrossOrigin(origins = "*")
@SuppressWarnings("java:S5122")
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
            @RequestBody PagoDeudaRequest request) {

        // Full object in the request because the endpoint may evolve and ask for more than
        // just the amount of money paid, like the way it was paid.
        DeudaResponse updated = service.registrarPago(id, request.getMontoPago());
        return ResponseEntity.ok(updated);
    }
}