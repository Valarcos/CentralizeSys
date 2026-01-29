package com.centralizesys;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@SpringBootApplication
@EnableScheduling
public class CentralizeSysApplication {

    private static final Logger log = LoggerFactory.getLogger(CentralizeSysApplication.class);
    private static final String DATA_DIR = "data";
    private static final String DB_NAME = "centralizesys.db";
    private static final String RESTORE_FILE = "centralizesys.restore";
    private static final String BACKUP_SUFFIX = ".pre_restore.bak";

    public static void main(String[] args) {
        // --- SYSTEM RESTORE LOGIC ---
        // Runs before Spring Context to avoid file locking
        performRestoreIfPending();

        SpringApplication.run(CentralizeSysApplication.class, args);
    }

    private static void performRestoreIfPending() {
        try {
            Path restorePath = Paths.get(DATA_DIR, RESTORE_FILE);
            if (Files.exists(restorePath)) {
                log.info(">>> SYSTEM RESTORE DETECTED: Restoring database...");

                Path targetDb = Paths.get(DATA_DIR, DB_NAME);

                // BACKUP CURRENT (Just in case)
                if (Files.exists(targetDb)) {
                    Path backupPath = Paths.get(DATA_DIR, DB_NAME + BACKUP_SUFFIX);
                    Files.copy(targetDb, backupPath, StandardCopyOption.REPLACE_EXISTING);
                }

                // RESTORE
                Files.copy(restorePath, targetDb, StandardCopyOption.REPLACE_EXISTING);

                // CLEANUP
                Files.delete(restorePath);

                log.info(">>> SYSTEM RESTORE COMPLETED SUCCESSFULLY.");
            }
        } catch (IOException e) {
            log.error(">>> CRITICAL: SYSTEM RESTORE FAILED!", e);
            // We do NOT exit, to allow admin to fix.
        }
    }
}