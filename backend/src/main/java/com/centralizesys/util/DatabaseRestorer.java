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

    private DatabaseRestorer() {
        // Utility class
    }

    /**
     * Checks for the existence of a 'centralizesys.restore' file.
     * If found, performs the atomic swap:
     * 1. Backup current DB to 'backups/checkpoints/pre_restore_[timestamp].db'
     * 2. Move .restore file to .db
     * 3. Clean up .restore file
     */
    public static void checkAndRestore() {
        try {
            Path restoreFile = DataPathConfig.resolve("data/centralizesys.restore");
            if (!Files.exists(restoreFile)) {
                return; // Nothing to do
            }

            log.warn(">>> RESTORE TRIGGER DETECTED! Starting restoration process... <<<");

            backupCurrentDatabase();
            restoreDatabase(restoreFile);

            log.info(">>> RESTORATION SUCCESSFUL! System will start with new data. <<<");

        } catch (IOException e) {
            log.error(">>> CRITICAL FAILURE DURING RESTORATION <<<", e);
        }
    }

    private static void backupCurrentDatabase() throws IOException {
        Path dbFile = DataPathConfig.resolve("data/centralizesys.db");
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
        Path dbFile = DataPathConfig.resolve("data/centralizesys.db");
        log.info("Restoring database from: {}", restoreFile);
        Files.move(restoreFile, dbFile, StandardCopyOption.REPLACE_EXISTING);
    }
}

