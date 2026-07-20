package com.centralizesys.controller;

import com.centralizesys.model.dto.PageResponse;
import com.centralizesys.model.gastos.GastoCaja;
import com.centralizesys.model.gastos.GastoCajaAnulacionRequest;
import com.centralizesys.model.gastos.GastoCajaRequest;
import com.centralizesys.security.SecurityUtils;
import com.centralizesys.service.GastoCajaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/gastos")
public class GastoCajaController {

    private final GastoCajaService gastoCajaService;

    public GastoCajaController(GastoCajaService gastoCajaService) {
        this.gastoCajaService = gastoCajaService;
    }

    /**
     * Returns a paginated list of Gastos, optionally filtered by date.
     *
     * @param page  0-indexed page number (default 0).
     * @param size  Page size (default 15).
     * @param year  Filter year (nullable).
     * @param month Filter month 1-12 (nullable).
     * @param day   Filter day 1-31 (nullable).
     */
    @GetMapping
    public ResponseEntity<PageResponse<GastoCaja>> getGastos(
            @RequestParam(defaultValue = "0")  Long    page,
            @RequestParam(defaultValue = "15") Long    size,
            @RequestParam(required = false)    Integer year,
            @RequestParam(required = false)    Integer month,
            @RequestParam(required = false)    Integer day) {
        return ResponseEntity.ok(gastoCajaService.obtenerGastos(page, size, year, month, day));
    }

    @PostMapping
    public ResponseEntity<Long> crearGasto(@Valid @RequestBody GastoCajaRequest request) {
        Long authenticatedUserId = SecurityUtils.getAuthenticatedUserId();
        Long id = gastoCajaService.crearGasto(request, authenticatedUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    @PostMapping("/{id}/anular")
    public ResponseEntity<Void> anularGasto(
            @PathVariable Long id,
            @RequestBody(required = false) GastoCajaAnulacionRequest request) {
        Long authenticatedUserId = SecurityUtils.getAuthenticatedUserId();
        gastoCajaService.anularGasto(id, request, authenticatedUserId);
        return ResponseEntity.ok().build();
    }
}
