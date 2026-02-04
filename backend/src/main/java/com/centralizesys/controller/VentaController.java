package com.centralizesys.controller;

import com.centralizesys.model.sales.Venta;
import com.centralizesys.model.sales.MetodoPago;
import com.centralizesys.model.sales.VentaRequest;
import com.centralizesys.model.sales.VentaResponse;
import com.centralizesys.repository.MetodoPagoRepository;
import com.centralizesys.service.VentaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ventas")
public class VentaController {

    private final VentaService ventaService;
    private final MetodoPagoRepository metodoPagoRepository;

    // We inject MetodoPagoRepository directly for simple reads (Dropdowns),
    // but use VentaService for the complex Transactional logic.
    public VentaController(VentaService ventaService, MetodoPagoRepository metodoPagoRepository) {
        this.ventaService = ventaService;
        this.metodoPagoRepository = metodoPagoRepository;
    }

    // --- TRANSACTIONS ---

    @PostMapping
    public ResponseEntity<VentaResponse> registrarVenta(@RequestBody VentaRequest request) {
        VentaResponse response = ventaService.registrarVenta(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // --- QUERIES ---

    // GET /api/ventas (History List - Headers only for performance)
    @GetMapping
    public ResponseEntity<List<Venta>> getAll() {
        return ResponseEntity.ok(ventaService.getAllVentas());
    }

    // GET /api/ventas/{id} (Full Receipt: Header + Items + Payments)
    @GetMapping("/{id}")
    public ResponseEntity<VentaResponse> getById(@PathVariable Long id) {
        VentaResponse response = ventaService.getVentaById(id);
        return ResponseEntity.ok(response);
    }

    // --- CONFIG / UTILS ---

    // GET /api/ventas/metodos-pago
    // Vital for the Frontend to populate the "Payment Method" dropdown
    @GetMapping("/metodos-pago")
    public ResponseEntity<List<MetodoPago>> getMetodosPago() {
        return ResponseEntity.ok(metodoPagoRepository.findAll());
    }
}