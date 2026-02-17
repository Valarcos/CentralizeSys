package com.centralizesys.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class BackupServiceIntegrationTest {

    @TempDir
    static Path tempDir;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public DataSource dataSource() {
            // Use DriverManagerDataSource (no pooling) to avoid file locks on Windows
            // allowing @TempDir cleanup to succeed.
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("org.sqlite.JDBC");
            ds.setUrl("jdbc:sqlite:" + tempDir.resolve("integration_test.db").toAbsolutePath());
            return ds;
        }
    }

    @Autowired
    private BackupService backupService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ResourceLoader resourceLoader;

    @MockBean
    private BackupPathStrategy pathStrategy;

    @BeforeEach
    void setupDatabase() {
        // 1. Configure Mock Strategy to return Safe Temp Paths
        String daily = tempDir.resolve("daily").toAbsolutePath().toString();
        String manual = tempDir.resolve("manual").toAbsolutePath().toString();
        String checkpoints = tempDir.resolve("checkpoints").toAbsolutePath().toString();
        Path restoreTrigger = tempDir.resolve("centralizesys.restore").toAbsolutePath();

        when(pathStrategy.getDailyDir()).thenReturn(daily);
        when(pathStrategy.getManualDir()).thenReturn(manual);
        when(pathStrategy.getCheckpointsDir()).thenReturn(checkpoints);
        when(pathStrategy.getRestoreTriggerPath()).thenReturn(restoreTrigger);

        // 2. Initialize Database Schema
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(resourceLoader.getResource("classpath:schema.sql"));
        populator.setSeparator(";;");
        populator.execute(dataSource);
    }

    @Test
    void testManualBackup() throws Exception {
        // 1. Cleanup before test (Safe now - only deletes from TEMP dir)
        File manualDir = new File(pathStrategy.getManualDir());
        if (manualDir.exists()) {
            File[] files = manualDir.listFiles();
            if (files != null) {
                for (File f : files)
                    Files.delete(f.toPath());
            }
        }

        // 2. Prepare Data
        jdbcTemplate.execute("INSERT INTO ubicaciones (nombre) VALUES ('Deposito Central')");
        jdbcTemplate.execute(
                "INSERT INTO productos (descripcion, codigo, precio_costo, precio_minorista, cantidad_stock) " +
                        "VALUES ('Test Product', 'TP-001', 10.0, 20.0, 100)");

        Long userId = 1L;

        // 3. Perform Backup
        backupService.performBackup(BackupService.BackupType.MANUAL, userId);

        // 4. Verify Files Created in Temp Dir
        File[] files = manualDir.listFiles((d, name) -> name.endsWith(".db"));
        assertTrue(files != null && files.length > 0, "Should have created a .db backup file");

        File[] excelFiles = manualDir.listFiles((d, name) -> name.endsWith(".xlsx"));
        assertTrue(excelFiles != null && excelFiles.length > 0, "Should have created an .xlsx backup file");
    }

    @Test
    void testRestoreScheduling() throws Exception {
        // 1. Create a dummy backup db in the temp daily dir
        File dailyDir = new File(pathStrategy.getDailyDir());
        if (!dailyDir.exists())
            assertTrue(dailyDir.mkdirs());

        File backupDb = new File(dailyDir, "test_restore.db");
        assertTrue(backupDb.createNewFile(), "Failed to create dummy backup db");

        // 2. Schedule Restore
        backupService.scheduleRestore("test_restore.db", 1L);

        // 3. Verify .restore file exists in temp dir
        File restoreFile = pathStrategy.getRestoreTriggerPath().toFile();
        assertTrue(restoreFile.exists(), "Restore trigger file should be created in temp dir");
    }

    @Test
    void testCheckpoints() {
        // 1. Create Checkpoint
        String filename = backupService.createCheckpoint("Test Reason", 1L);

        // 2. Verify existence
        File checkpointsDir = new File(pathStrategy.getCheckpointsDir());
        File checkpoint = new File(checkpointsDir, filename);
        assertTrue(checkpoint.exists());

        // 3. Cleanup logic
        backupService.cleanupCheckpoints();
    }
}
