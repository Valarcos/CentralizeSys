package com.centralizesys.model.dashboard;

import com.centralizesys.model.cheque.AlertaCheque;
import com.centralizesys.model.dto.BackupFileDTO;
import com.centralizesys.model.product.ProductResponse;

import java.util.List;

/**
 * Composite DTO aggregating high-frequency dashboard telemetry and alerts.
 * Eliminates frontend HTTP request fan-out by combining stock warnings, backup recency,
 * active debt presence, cheque due-dates, and critical system notifications into a single atomic payload.
 */
public record DashboardSummaryResponse(
        List<ProductResponse> stockAlerts,
        BackupFileDTO lastBackup,
        boolean hasActiveDebts,
        List<AlertaCheque> pendingCheques,
        List<SystemAlertDTO> systemAlerts
) {
    // TODO: [ARQUITECTURA / TIPADO ESTRICTO - AUDITORÍA DE DEUDA TÉCNICA]
    // ---------------------------------------------------------------------------------------------
    // Contexto:
    // Siguiendo los estándares de arquitectura limpia y 'GEMINI.md §4 (Strict typing, no magic values)',
    // este DTO compuesto formaliza sus alertas mediante 'SystemAlertDTO' en lugar de estructuras genéricas.
    //
    // Tarea pendiente post-hotfix:
    // Analizar e implementar sub-records fuertemente tipados en otras áreas del sistema donde persisten
    // retornos de 'Map<String, Object>' en crudo producto de 'jdbcTemplate.queryForList(...)':
    // 1. 'UnifiedViewService.java' (filas de transacciones combinadas de ventas y cobros).
    // 2. 'BackupService.java' (consultas SQL mapeadas directamente a celdas de hojas Excel).
    // 3. Consultas agregadas de auditoría y estadísticas dinámicas en 'ReportesService.java'.
    //
    // Medidas de seguridad y prueba requeridas:
    // - Garantizar que la refactorización a Records no altere los nombres de claves JSON esperadas por la UI.
    // - Incorporar tests unitarios de mapeo para asegurar cobertura y prevenir regresiones en SonarCloud.
}
