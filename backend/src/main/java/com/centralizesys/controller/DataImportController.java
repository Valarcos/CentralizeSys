package com.centralizesys.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@RestController
@RequestMapping("/api/import")
@CrossOrigin(origins = "*")
@SuppressWarnings("java:S5122") // Ignored: CORS accepted for local desktop usage
public class DataImportController {

    private static final Logger log = LoggerFactory.getLogger(DataImportController.class);
    private final com.centralizesys.service.LegacyFinancialImportService legacyService;

    public DataImportController(com.centralizesys.service.LegacyFinancialImportService legacyService) {
        this.legacyService = legacyService;
    }

    @PostMapping("/legacy")
    public ResponseEntity<Map<String, String>> importLegacyExcel(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        Path tempPath = null;
        try {
            // Use a dedicated, controlled directory instead of the system default to avoid "Shared Directory" security risks.
            Path appTempDir = Paths.get("data/temp_imports");
            if (!Files.exists(appTempDir)) {
                Files.createDirectories(appTempDir);
            }

            // Create temp file in our controlled directory
            tempPath = Files.createTempFile(appTempDir, "legacy_import_", ".xlsx");

            file.transferTo(tempPath.toFile());

            String report = legacyService.importLegacyFile(tempPath.toAbsolutePath().toString());

            return ResponseEntity.ok(Map.of(
                    "message", "Import Processed",
                    "details", report));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Import Failed: " + e.getMessage()));
        } finally {
            // Clean up
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (Exception e) {
                    // Industry Standard: Log the warning so Ops knows disk space might leak,
                    // but do not crash the valid response.
                    log.warn("Failed to delete temp file: {}. Error: {}", tempPath, e.getMessage());
                }
            }
        }
    }
}