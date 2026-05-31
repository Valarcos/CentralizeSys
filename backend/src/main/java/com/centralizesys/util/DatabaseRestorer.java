package com.centralizesys.util;

import com.centralizesys.config.DataPathConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

/**
 * Utility class to handle Database Restoration logic BEFORE Spring Context
 * initialization.
 * This ensures the DB file is swapped while no connections are active.
 */
public class DatabaseRestorer {

    //TODO: Fix sonar issues on this file
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

            log.warn(">>> RESTORE TRIGGER DETECTED! However, database restoration is temporarily disabled pending full PostgreSQL psql migration. <<<");

            // Delete the restore file since it cannot be used with PostgreSQL
            Files.deleteIfExists(restoreFile);
            log.info("Ignored and deleted .restore file as it is incompatible with the current database engine.");

        } catch (IOException e) {
            log.error(">>> FAILURE DURING RESTORE CLEANUP <<<", e);
        }
    }
}
