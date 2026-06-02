package com.centralizesys.controller;

import com.centralizesys.service.BackupService;
import com.centralizesys.model.dto.BackupFileDTO;
import com.centralizesys.security.SecurityUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

@RestController
@RequestMapping("/api/backups")
public class BackupController {

    private static final Logger log = LoggerFactory.getLogger(BackupController.class);
    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @PostMapping("/create")
    public ResponseEntity<String> triggerManualBackup() {
        Long userId = SecurityUtils.getAuthenticatedUserId();
        backupService.performBackup(BackupService.BackupType.MANUAL, userId);
        return ResponseEntity.ok("Backup manual iniciado. Verifique el registro de auditoría.");
    }

    @GetMapping
    public ResponseEntity<List<BackupFileDTO>> listBackups() {
        return ResponseEntity.ok(backupService.listBackups());
    }

    @GetMapping("/last")
    public ResponseEntity<BackupFileDTO> getLastBackup() {
        List<BackupFileDTO> backups = backupService.listBackups();
        if (backups.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        // List is already sorted DESC by date in service
        return ResponseEntity.ok(backups.get(0));
    }

    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> downloadBackup(@PathVariable String filename) {
        try {
            File file = backupService.getBackupFile(filename);
            if (!file.exists()) {
                throw new FileNotFoundException("File not found: " + filename);
            }

            Resource resource = new FileSystemResource(file);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(file.length())
                    .body(resource);
        } catch (FileNotFoundException e) {
            log.warn("Download failed: File not found {}", filename);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Download failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/restore/{filename}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> restoreDatabase(@PathVariable String filename) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_IMPLEMENTED)
                .body("Restauración deshabilitada temporalmente (migración a PostgreSQL en proceso).");
    }

    @PostMapping(value = "/upload-restore", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> restoreFromUpload(
            @RequestPart("file") org.springframework.web.multipart.MultipartFile file) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_IMPLEMENTED)
                .body("Carga de restauración deshabilitada temporalmente (migración a PostgreSQL en proceso).");
    }
}
