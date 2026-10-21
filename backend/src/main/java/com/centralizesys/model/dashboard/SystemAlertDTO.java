package com.centralizesys.model.dashboard;

/**
 * Strongly-typed system alert DTO for infrastructure, backup, and operational notices.
 * Replaces generic Map<String, String> to guarantee contract safety and compile-time verification.
 */
public record SystemAlertDTO(
        String message,
        String severity, // "INFO", "WARNING", "CRITICAL"
        String source    // "BACKUP", "DB", "SECURITY", "SYSTEM"
) {}
