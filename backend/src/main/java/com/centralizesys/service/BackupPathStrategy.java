package com.centralizesys.service;

import java.nio.file.Path;

/**
 * Strategy interface for resolving backup-related file system paths.
 * Allows BackupService to be tested without touching production files.
 *
 * Production: DefaultBackupPathStrategy (delegates to DataPathConfig)
 * Tests: Inject a test implementation that returns @TempDir paths
 */
public interface BackupPathStrategy {

    String getDailyDir();

    String getManualDir();

    String getCheckpointsDir();

    /**
     * Path to the restore trigger file (e.g., data/centralizesys.restore).
     * Used by scheduleRestore() and restoreFromUpload().
     */
    Path getRestoreTriggerPath();
}
