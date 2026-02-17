package com.centralizesys.util;

import com.centralizesys.config.DataPathConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class to handle Database Restoration logic BEFORE Spring Context
 * initialization.
 * This ensures the DB file is swapped while no connections are active.
 */
public class DatabaseRestorer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseRestorer.class);
    private static final DateTimeFormatter FMT_FILE = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String DB_PATH = "data/centralizesys.db";

    /**
     * Minimum acceptable size for a restore file (10 KB).
     * A valid SQLite DB with schema + seed data is at least ~40 KB.
     * Anything under 10 KB is either empty, corrupted, or a test artifact.
     */
    static final long MIN_RESTORE_FILE_SIZE_BYTES = 10_240L;

    /**
     * Flag file written when a restore is skipped due to validation failure.
     * Consumed by SystemStatusController to show a one-time alert to the user.
     */
    public static final String RESTORE_FAILED_FLAG = "data/restore_failed.flag";

    private DatabaseRestorer() {
        // Utility class
    }

    /**
     * Checks for the existence of a 'centralizesys.restore' file.
     * If found, validates it and performs the atomic swap:
     * 1. Validate restore file size (reject suspiciously small files)
     * 2. Backup current DB to 'backups/checkpoints/pre_restore_[timestamp].db'
     * 3. Move .restore file to .db
     * 4. Clean up .restore file
     */
    public static void checkAndRestore() {
        try {
            Path restoreFile = DataPathConfig.resolve("data/centralizesys.restore");
            if (!Files.exists(restoreFile)) {
                return; // Nothing to do
            }

            log.warn(">>> RESTORE TRIGGER DETECTED! Starting restoration process... <<<");

            // --- SIZE VALIDATION ---
            long restoreSize = Files.size(restoreFile);
            Path dbFile = DataPathConfig.resolve(DB_PATH);
            long currentDbSize = Files.exists(dbFile) ? Files.size(dbFile) : 0;

            log.info("Restore file size: {} bytes | Current DB size: {} bytes", restoreSize, currentDbSize);

            if (restoreSize < MIN_RESTORE_FILE_SIZE_BYTES) {
                log.error(">>> CRITICAL: Restore file is suspiciously small ({} bytes, minimum required: {} bytes). " +
                        "SKIPPING RESTORATION to prevent data loss. <<<", restoreSize, MIN_RESTORE_FILE_SIZE_BYTES);

                // Write flag file for the frontend alert
                writeRestoreFailedFlag(restoreSize);

                // Delete the invalid restore file to prevent re-triggering on next restart
                Files.deleteIfExists(restoreFile);
                log.warn("Invalid restore file deleted to prevent repeated trigger.");
                return;
            }

            backupCurrentDatabase();
            restoreDatabase(restoreFile);

            log.info(">>> RESTORATION SUCCESSFUL! System will start with new data. <<<");

        } catch (IOException e) {
            log.error(">>> CRITICAL FAILURE DURING RESTORATION <<<", e);
        }
    }

    private static void writeRestoreFailedFlag(long restoreSize) {
        try {
            Path flagFile = DataPathConfig.resolve(RESTORE_FAILED_FLAG);
            Path parent = flagFile.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            String content = "RESTORE_SKIPPED|size=" + restoreSize
                    + "|min_required=" + MIN_RESTORE_FILE_SIZE_BYTES
                    + "|timestamp=" + LocalDateTime.now().format(FMT_FILE);
            Files.writeString(flagFile, content);
            log.info("Restore failure flag written to: {}", flagFile);
        } catch (IOException e) {
            log.error("Failed to write restore failure flag file", e);
        }
    }

    private static void backupCurrentDatabase() throws IOException {
        Path dbFile = DataPathConfig.resolve(DB_PATH);
        if (!Files.exists(dbFile)) {
            return;
        }

        Path checkpointsDir = DataPathConfig.resolve("backups/checkpoints");
        if (!Files.exists(checkpointsDir)) {
            Files.createDirectories(checkpointsDir);
        }

        String timestamp = LocalDateTime.now().format(FMT_FILE);
        Path safetyBackup = checkpointsDir.resolve("pre_restore_" + timestamp + ".db");

        log.info("Creating safety backup: {}", safetyBackup);
        Files.copy(dbFile, safetyBackup, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void restoreDatabase(Path restoreFile) throws IOException {
        Path dbFile = DataPathConfig.resolve(DB_PATH);
        log.info("Restoring database from: {}", restoreFile);
        Files.move(restoreFile, dbFile, StandardCopyOption.REPLACE_EXISTING);
    }
}
