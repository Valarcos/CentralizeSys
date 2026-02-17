package com.centralizesys.service;

import com.centralizesys.config.DataPathConfig;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Production implementation of BackupPathStrategy.
 * Delegates all path resolution to DataPathConfig (the real project root).
 *
 * This is functionally identical to the old static constants in BackupService,
 * but now injectable and testable.
 */
@Component
public class DefaultBackupPathStrategy implements BackupPathStrategy {

    @Override
    public String getDailyDir() {
        return DataPathConfig.resolveString("backups/daily");
    }

    @Override
    public String getManualDir() {
        return DataPathConfig.resolveString("backups/manual");
    }

    @Override
    public String getCheckpointsDir() {
        return DataPathConfig.resolveString("backups/checkpoints");
    }

    @Override
    public Path getRestoreTriggerPath() {
        return DataPathConfig.resolve("data/centralizesys.restore");
    }
}
