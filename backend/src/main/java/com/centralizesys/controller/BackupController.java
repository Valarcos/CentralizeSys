package com.centralizesys.controller;

import com.centralizesys.service.BackupService;
import com.centralizesys.model.dto.BackupFileDTO;
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
@RequestMapping("/api/backup")
@CrossOrigin(origins = "*")
@SuppressWarnings("java:S5122") // Ignored: CORS accepted for local desktop usage
public class BackupController {

    private static final Logger log = LoggerFactory.getLogger(BackupController.class);
    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @PostMapping("/now")
    // TODO: the moment we implement Spring Security, this must be rewritten to utilize Spring and capture the authenticated UserID
    public ResponseEntity<String> triggerManualBackup(
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        backupService.performBackup(BackupService.BackupType.MANUAL, userId);
        return ResponseEntity.ok("Backup manual iniciado. Verifique el registro de auditoría.");
    }

    @GetMapping("/list")
    public ResponseEntity<List<BackupFileDTO>> listBackups() {
        return ResponseEntity.ok(backupService.listBackups());
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

    // TODO: SPRING_SECURITY_MIGRATION - [Context: Restore operation]
    @PostMapping("/restore/{filename}")
    public ResponseEntity<String> restoreDatabase(@PathVariable String filename,
                                                  @RequestParam(defaultValue = "false") boolean confirm,
                                                  @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        if (!confirm) {
            return ResponseEntity.badRequest()
                    .body("DANGER: This will overwrite the entire database. Send '?confirm=true' to proceed.");
        }

        try {
            backupService.scheduleRestore(filename, userId);
            return ResponseEntity.ok("System Restore Scheduled. Please RESTART the server to apply changes.");
        } catch (Exception e) {
            log.error("Restore scheduling failed", e);
            return ResponseEntity.internalServerError().body("Restore Failed: " + e.getMessage());
        }
    }
}
