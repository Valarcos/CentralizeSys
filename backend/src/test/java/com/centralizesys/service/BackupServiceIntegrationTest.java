package com.centralizesys.service;

import com.centralizesys.config.DataPathConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class BackupServiceIntegrationTest {

    // Use DynamicPropertySource to set the database URL using DataPathConfig
    @DynamicPropertySource
    static void setDatabaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DataPathConfig::getDatabaseUrl);
    }

    @Autowired
    private BackupService backupService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ResourceLoader resourceLoader;

    @BeforeEach
    void setupDatabase() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(resourceLoader.getResource("classpath:schema.sql"));
        populator.setSeparator(";;");
        populator.execute(dataSource);
    }

    @Test
    void testManualBackup() throws Exception {
        // 1. Cleanup before test (Aggressive Cascade)
        // Helper to ignore "no such table" errors
        try {
            jdbcTemplate.execute("DELETE FROM stock_por_ubicacion");
        } catch (Exception e) {
            // Table might not exist yet, safe to ignore
        }
        try {
            jdbcTemplate.execute("DELETE FROM detalles_compra");
        } catch (Exception e) {
            // Table might not exist yet, safe to ignore
        }
        try {
            jdbcTemplate.execute("DELETE FROM detalles_venta");
        } catch (Exception e) {
            // Table might not exist yet, safe to ignore
        }
        try {
            jdbcTemplate.execute("DELETE FROM productos");
        } catch (Exception e) {
            // Table might not exist yet, safe to ignore
        }

        jdbcTemplate.execute(
                "INSERT INTO productos (codigo, descripcion, precio_costo, precio_mayorista, precio_minorista, cantidad_stock) VALUES ('P1', 'Test backup', 10.0, 15.0, 20.0, 100)");

        File manualDir = DataPathConfig.resolve("backups/manual").toFile();
        if (manualDir.exists()) {
            for (File f : java.util.Objects.requireNonNull(manualDir.listFiles())) {
                boolean deleted = f.delete();
                assertTrue(deleted, "Failed to clean up file: " + f.getName());
            }
        }

        // 2. Perform Manual Backup
        // Using User ID 2L to test it is recorded in audit
        long userId = 2L;
        backupService.performBackup(BackupService.BackupType.MANUAL, userId);

        // 3. Verify Files
        assertTrue(manualDir.exists(), "Manual backup dir should exist");
        File[] files = manualDir.listFiles();

        assertTrue(files != null && files.length >= 2, "Should have at least 2 files (DB + Excel)");

        boolean hasDb = false;
        File excelFile = null;
        for (File f : files) {
            if (f.getName().endsWith(".db"))
                hasDb = true;
            if (f.getName().endsWith(".xlsx"))
                excelFile = f;
        }
        assertTrue(hasDb, "DB backup file missing");
        org.junit.jupiter.api.Assertions.assertNotNull(excelFile, "Excel export file missing");

        // 4. Verify Excel Content (New Check)
        try (org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.FileInputStream(excelFile))) {
            org.junit.jupiter.api.Assertions.assertNotNull(workbook.getSheet("Productos"), "Sheet 'Productos' missing");
            org.junit.jupiter.api.Assertions.assertNotNull(workbook.getSheet("Ventas"), "Sheet 'Ventas' missing");
            org.junit.jupiter.api.Assertions.assertNotNull(workbook.getSheet("Compras"), "Sheet 'Compras' missing");
            org.junit.jupiter.api.Assertions.assertNotNull(workbook.getSheet("Deudores"), "Sheet 'Deudores' missing");
            org.junit.jupiter.api.Assertions.assertNotNull(workbook.getSheet("Stock_Ubicacion"),
                    "Sheet 'Stock_Ubicacion' missing");
            org.junit.jupiter.api.Assertions.assertNotNull(workbook.getSheet("Auditoria"), "Sheet 'Auditoria' missing");
        }

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auditoria WHERE accion = 'BACKUP_EXITOSO' AND detalles LIKE '%manual%'",
                Integer.class);
        assertTrue(count != null && count > 0, "Audit log missing or Backup Failed");
    }

    @Test
    void testCleanupLogic() throws Exception {
        // 1. Setup Daily Dir
        File dailyDir = DataPathConfig.resolve("backups/daily").toFile();
        if (!dailyDir.exists())
            assertTrue(dailyDir.mkdirs(), "Failed to create daily backup dir");

        // 2. Create OLD file (90 days old) - NOT 1st or 15th
        // Target: Delete
        File oldToDelete = new File(dailyDir, "centralizesys_daily_20200520_1000.db");
        if (oldToDelete.exists())
            assertTrue(oldToDelete.delete(), "Clean setup failed");
        assertTrue(oldToDelete.createNewFile(), "Failed to create test file");
        long oldTime = java.time.LocalDateTime.now().minusDays(90).atZone(java.time.ZoneId.systemDefault()).toInstant()
                .toEpochMilli();
        assertTrue(oldToDelete.setLastModified(oldTime), "Failed to set last modified time");

        // 3. Create OLD file (90 days old) - ON 1st
        // Target: Keep
        File oldToKeep = new File(dailyDir, "centralizesys_daily_20200501_1000.db");
        if (oldToKeep.exists())
            assertTrue(oldToKeep.delete(), "Clean setup failed");
        assertTrue(oldToKeep.createNewFile(), "Failed to create test file");
        assertTrue(oldToKeep.setLastModified(oldTime), "Failed to set last modified time");

        // 4. Run Cleanup
        backupService.cleanupOldBackups();

        // 5. Verify
        org.junit.jupiter.api.Assertions.assertFalse(oldToDelete.exists(), "Should delete old file not on 1st/15th");
        assertTrue(oldToKeep.exists(), "Should keep old file on 1st");

        // Cleanup
        boolean deleted = oldToKeep.delete();
        assertTrue(deleted, "Failed to clean up test file: " + oldToKeep.getName());
    }

    @Test
    void testRestoreScheduling() throws Exception {
        // 1. Create a dummy backup db
        File dailyDir = DataPathConfig.resolve("backups/daily").toFile();
        if (!dailyDir.exists())
            assertTrue(dailyDir.mkdirs(), "Failed to create daily dir");
        // Clean possible leftovers
        File backupDb = new File(dailyDir, "test_restore.db");
        if (backupDb.exists())
            assertTrue(backupDb.delete(), "Failed to clean up existing backup db");

        assertTrue(backupDb.createNewFile(), "Failed to create dummy backup db");

        // 2. Schedule Restore
        backupService.scheduleRestore("test_restore.db", 1L);

        // 3. Verify .restore file exists
        File restoreFile = DataPathConfig.resolve("data/centralizesys.restore").toFile();
        assertTrue(restoreFile.exists(), "Restore trigger file should be created");

        // Cleanup
        boolean dbDeleted = backupDb.delete();
        boolean restoreDeleted = restoreFile.delete();
        assertTrue(dbDeleted, "Failed to clean up backupDb");
        assertTrue(restoreDeleted, "Failed to clean up restoreFile");
    }
}
