package com.centralizesys.controller;

import com.centralizesys.model.sales.Venta;
import com.centralizesys.model.sales.MetodoPago;
import com.centralizesys.model.sales.VentaRequest;
import com.centralizesys.model.sales.VentaResponse;
import com.centralizesys.repository.MetodoPagoRepository;
import com.centralizesys.security.SecurityUtils;
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
        // Security: Always override the usuarioId from the validated JWT.
        // The value sent by the client in the request body is discarded.
        request.setUsuarioId(SecurityUtils.getAuthenticatedUserId());
        VentaResponse response = ventaService.registrarVenta(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // --- QUERIES ---

    // GET /api/ventas (Paginated & Filtered)
    // Default: Page 0, Size 20, Last 30 Days (Service handles defaults)
    @GetMapping
    public ResponseEntity<com.centralizesys.model.dto.PageResponse<Venta>> getAll(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ventaService.getVentasPage(startDate, endDate, page, size));
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

    @GetMapping("/clientes")
    public ResponseEntity<List<String>> getClientes() {
        return ResponseEntity.ok(ventaService.getClientes());
    }
}