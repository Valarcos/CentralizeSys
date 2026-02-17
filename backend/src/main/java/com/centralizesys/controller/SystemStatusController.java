package com.centralizesys.controller;

import com.centralizesys.config.DataPathConfig;
import com.centralizesys.util.DatabaseRestorer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private static final Logger log = LoggerFactory.getLogger(SystemStatusController.class);

    /**
     * Returns a list of active system alerts.
     * Currently checks for restore-failure flag files.
     * Flag files are consumed (deleted) after reading — alerts show once.
     */
    @GetMapping("/alerts")
    public ResponseEntity<Map<String, List<Map<String, String>>>> getSystemAlerts() {
        List<Map<String, String>> alerts = new ArrayList<>();

        checkRestoreFailedFlag(alerts);

        return ResponseEntity.ok(Map.of("alerts", alerts));
    }

    private void checkRestoreFailedFlag(List<Map<String, String>> alerts) {
        Path flagFile = DataPathConfig.resolve(DatabaseRestorer.RESTORE_FAILED_FLAG);

        if (!Files.exists(flagFile)) {
            return;
        }

        String flagContent = "Detalles no disponibles (archivo corrupto o ilegible)";
        try {
            flagContent = Files.readString(flagFile);
        } catch (IOException e) {
            log.warn(
                    "Could not read flag file as UTF-8 (likely Windows/PowerShell encoding). Attempting UTF-16 fallback.",
                    e);
            try {
                // Windows PowerShell default: UTF-16 usually with BOM
                flagContent = Files.readString(flagFile, java.nio.charset.StandardCharsets.UTF_16);
            } catch (IOException ex1) {
                log.warn("Could not read flag file as UTF-16 either. Attempting ISO-8859-1 (raw bytes) fallback.", ex1);
                try {
                    // Last resort: read as raw extended ASCII bytes so we at least get *something*
                    // (even if garbled)
                    flagContent = Files.readString(flagFile, java.nio.charset.StandardCharsets.ISO_8859_1);
                } catch (IOException ex2) {
                    log.error("Error reading restore failure flag even with fallbacks", ex2);
                }
            }
        }

        alerts.add(Map.of(
                "type", "RESTORE_FAILED",
                "message", "Ocurrió un error con la restauración de la DB a un punto anterior en el tiempo. " +
                        "Contactar al administrador y revisar los logs (logs/app.log).",
                "details", flagContent));

        // Consume the flag — one-time display
        try {
            Files.deleteIfExists(flagFile);
            log.info("Restore failure flag consumed and deleted.");
        } catch (IOException e) {
            log.error("Error deleting restore failure flag", e);
        }
    }
}
