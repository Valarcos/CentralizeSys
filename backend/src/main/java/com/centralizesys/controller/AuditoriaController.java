package com.centralizesys.controller;

import com.centralizesys.model.audit.Auditoria;
import com.centralizesys.service.AuditoriaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

// TODO: Configure specific origins for production security
@RestController
@RequestMapping("/api/auditoria")
@CrossOrigin(origins = "*")
@SuppressWarnings("java:S5122") // Ignored: CORS accepted for local desktop usage
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    /**
     * Retrieves audit logs.
     * Defaults to the last 30 days if no dates are provided.
     * Format: YYYY-MM-DDTHH:mm:ss (ISO 8601)
     */
    @GetMapping
    public ResponseEntity<List<Auditoria>> getLogs(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        // Default: Last 15 days window
        String start = (from != null) ? from : LocalDateTime.now().minusDays(15).toString();
        String end = (to != null) ? to : LocalDateTime.now().toString();

        List<Auditoria> logs = auditoriaService.getLogsByDateRange(start, end);
        return ResponseEntity.ok(logs);
    }
}