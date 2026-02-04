package com.centralizesys.controller;

import com.centralizesys.model.audit.Auditoria;
import com.centralizesys.service.AuditoriaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/auditoria")
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
    @PreAuthorize("hasRole('ADMIN')")
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