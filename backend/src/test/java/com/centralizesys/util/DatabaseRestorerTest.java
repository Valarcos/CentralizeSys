package com.centralizesys.util;

import com.centralizesys.config.DataPathConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseRestorerTest {

    @TempDir
    Path tempDir;

    private MockedStatic<DataPathConfig> dataPathConfigMock;
    private Path dbFile;
    private Path restoreFile;
    private Path checkpointsDir;

    @BeforeEach
    void setUp() throws IOException {
        // Mock DataPathConfig to use our temp directory
        dataPathConfigMock = Mockito.mockStatic(DataPathConfig.class);

        Path dataDir = tempDir.resolve("data");
        Files.createDirectories(dataDir);

        Path backupDir = tempDir.resolve("backups/checkpoints");
        // Create full path
        Files.createDirectories(backupDir);

        dbFile = dataDir.resolve("centralizesys.db");
        restoreFile = dataDir.resolve("centralizesys.restore");
        checkpointsDir = backupDir;

        // Mock resolutions
        dataPathConfigMock.when(() -> DataPathConfig.resolve("data/centralizesys.restore")).thenReturn(restoreFile);
        dataPathConfigMock.when(() -> DataPathConfig.resolve("data/centralizesys.db")).thenReturn(dbFile);
        dataPathConfigMock.when(() -> DataPathConfig.resolve("backups/checkpoints")).thenReturn(checkpointsDir);
    }

    @AfterEach
    void tearDown() {
        dataPathConfigMock.close();
    }

    @Test
    void whenRestoreFileExists_shouldBackupAndSwap() throws IOException {
        // GIVEN
        Files.writeString(dbFile, "OLD_DATA");
        Files.writeString(restoreFile, "NEW_DATA");

        // WHEN
        DatabaseRestorer.checkAndRestore();

        // THEN
        // 1. Current DB should now have NEW content
        assertEquals("NEW_DATA", Files.readString(dbFile));

        // 2. Restore file should be gone
        assertFalse(Files.exists(restoreFile));

        // 3. A backup file should exist in checkpoints with OLD content
        try (var stream = Files.list(checkpointsDir)) {
            Path backup = stream
                    .filter(p -> p.getFileName().toString().startsWith("pre_restore_"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No backup file created"));

            assertEquals("OLD_DATA", Files.readString(backup));
        }
    }

    @Test
    void whenNoRestoreFile_shouldDoNothing() throws IOException {
        // GIVEN
        Files.writeString(dbFile, "OLD_DATA");

        // WHEN
        DatabaseRestorer.checkAndRestore();

        // THEN
        assertEquals("OLD_DATA", Files.readString(dbFile));
        assertEquals(0, Files.list(checkpointsDir).count());
    }

    @Test
    void whenDbDoesNotExist_shouldStillRestore() throws IOException {
        // GIVEN
        // DB file does not exist (fresh install or deleted)
        Files.writeString(restoreFile, "NEW_DATA");

        // WHEN
        DatabaseRestorer.checkAndRestore();

        // THEN
        assertEquals("NEW_DATA", Files.readString(dbFile));
        assertFalse(Files.exists(restoreFile));
        // No backup needed if no DB existed
        assertEquals(0, Files.list(checkpointsDir).count());
    }
}
