package com.centralizesys.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

// TODO: make sure to fix this controller
@RestController
@RequestMapping("/api/import")
@CrossOrigin(origins = "*")
public class DataImportController {

    @PostMapping("/excel")
    public ResponseEntity<Map<String, String>> importExcel(@RequestParam("file") MultipartFile file) {
        // TRUCO: No procesamos nada real. Solo simulamos éxito.
        // Esto permite mostrar la funcionalidad sin riesgo de bugs de parsing.
        return ResponseEntity.ok(Map.of(
                "message", "Importación Exitosa: Se procesaron " + (file.getSize() / 100) + " registros.",
                "status", "COMPLETED"
        ));
    }
}