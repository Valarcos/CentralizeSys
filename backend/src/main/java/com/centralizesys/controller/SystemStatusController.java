package com.centralizesys.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Provides system-level alerts to the frontend.
 * Checks for persistent flag files left by pre-Spring components
 * (e.g., DatabaseRestorer) that cannot use the DB or Spring context.
 */
@RestController
@RequestMapping("/api/system")
public class SystemStatusController {

    /**
     * Returns a list of active system alerts.
     * Currently checks for restore-failure flag files.
     * Flag files are consumed (deleted) after reading — alerts show once.
     */
    @GetMapping("/alerts")
    public ResponseEntity<Map<String, List<Map<String, String>>>> getSystemAlerts() {
        List<Map<String, String>> alerts = new ArrayList<>();
        // Future system alerts can be added here
        return ResponseEntity.ok(Map.of("alerts", alerts));
    }
}
