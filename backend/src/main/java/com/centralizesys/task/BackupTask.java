package com.centralizesys.task;

import com.centralizesys.service.BackupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BackupTask {

    private static final Logger log = LoggerFactory.getLogger(BackupTask.class);
    private final BackupService backupService;

    public BackupTask(BackupService backupService) {
        this.backupService = backupService;
    }

    // 13:00 ART Daily (Mid-Day Safety Net)
    @Scheduled(cron = "0 0 13 * * *", zone = "America/Argentina/Buenos_Aires")
    public void runMidDayBackup() {
        try {
            log.info("Starting Mid-Day Backup...");
            backupService.performBackup(BackupService.BackupType.DAILY, 0L);
            backupService.cleanupCheckpoints();
            log.info("Mid-Day Backup completed.");
        } catch (Exception e) {
            log.error("Mid-Day Backup failed", e);
        }
    }

    // 20:00 ART Daily (End of Day)
    @Scheduled(cron = "0 0 20 * * *", zone = "America/Argentina/Buenos_Aires")
    public void runDailyBackup() {
        try {
            log.info("Starting Daily Backup...");
            backupService.performBackup(BackupService.BackupType.DAILY, 0L);

            // Remove the 13:00 backup to save space (since 20:00 supersedes it)
            backupService.removeMidDayBackup();

            // Check retention immediately after backup
            backupService.cleanupOldBackups();
            backupService.cleanupCheckpoints();
            log.info("Daily Backup completed.");
        } catch (Exception e) {
            log.error("Daily Backup failed", e);
        }
    }
}
