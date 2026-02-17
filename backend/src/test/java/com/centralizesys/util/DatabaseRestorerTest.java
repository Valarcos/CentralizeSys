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
    private Path flagFile;

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
        flagFile = dataDir.resolve("restore_failed.flag");

        // Mock resolutions
        dataPathConfigMock.when(() -> DataPathConfig.resolve("data/centralizesys.restore")).thenReturn(restoreFile);
        dataPathConfigMock.when(() -> DataPathConfig.resolve("data/centralizesys.db")).thenReturn(dbFile);
        dataPathConfigMock.when(() -> DataPathConfig.resolve("backups/checkpoints")).thenReturn(checkpointsDir);
        dataPathConfigMock.when(() -> DataPathConfig.resolve(DatabaseRestorer.RESTORE_FAILED_FLAG))
                .thenReturn(flagFile);
    }

    @AfterEach
    void tearDown() {
        dataPathConfigMock.close();
    }

    @Test
    void whenRestoreFileExists_shouldBackupAndSwap() throws IOException {
        // GIVEN - a restore file larger than the minimum threshold
        Files.writeString(dbFile, "OLD_DATA");
        byte[] validData = new byte[(int) DatabaseRestorer.MIN_RESTORE_FILE_SIZE_BYTES + 1024];
        java.util.Arrays.fill(validData, (byte) 'X');
        Files.write(restoreFile, validData);

        // WHEN
        DatabaseRestorer.checkAndRestore();

        // THEN
        // 1. Current DB should now have the restore file content
        byte[] dbContent = Files.readAllBytes(dbFile);
        assertEquals(validData.length, dbContent.length);

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

        // 4. No failure flag should exist
        assertFalse(Files.exists(flagFile), "No failure flag should be written on successful restore");
    }

    @Test
    void whenNoRestoreFile_shouldDoNothing() throws IOException {
        // GIVEN
        Files.writeString(dbFile, "OLD_DATA");

        // WHEN
        DatabaseRestorer.checkAndRestore();

        // THEN
        assertEquals("OLD_DATA", Files.readString(dbFile));
        try (java.util.stream.Stream<Path> stream = Files.list(checkpointsDir)) {
            assertEquals(0, stream.count());
        }
        assertFalse(Files.exists(flagFile));
    }

    @Test
    void whenDbDoesNotExist_shouldStillRestore() throws IOException {
        // GIVEN - DB file does not exist (fresh install), restore is valid size
        byte[] validData = new byte[(int) DatabaseRestorer.MIN_RESTORE_FILE_SIZE_BYTES + 1024];
        java.util.Arrays.fill(validData, (byte) 'X');
        Files.write(restoreFile, validData);

        // WHEN
        DatabaseRestorer.checkAndRestore();

        // THEN
        byte[] dbContent = Files.readAllBytes(dbFile);
        assertEquals(validData.length, dbContent.length);
        assertFalse(Files.exists(restoreFile));
        // No backup needed if no DB existed
        try (java.util.stream.Stream<Path> stream = Files.list(checkpointsDir)) {
            assertEquals(0, stream.count());
        }
        assertFalse(Files.exists(flagFile));
    }

    @Test
    void whenRestoreFileIsTooSmall_shouldSkipAndWriteFlag() throws IOException {
        // GIVEN - a suspiciously small restore file (test artifact or corrupted)
        Files.writeString(dbFile, "VALID_PRODUCTION_DATA");
        Files.writeString(restoreFile, "TINY"); // Only 4 bytes — way below 10KB threshold

        // WHEN
        DatabaseRestorer.checkAndRestore();

        // THEN
        // 1. Original DB should be UNTOUCHED
        assertEquals("VALID_PRODUCTION_DATA", Files.readString(dbFile),
                "DB must NOT be overwritten by a suspiciously small restore file");

        // 2. Restore file should be deleted (prevent re-triggering on next restart)
        assertFalse(Files.exists(restoreFile),
                "Invalid restore file should be deleted to prevent repeated triggers");

        // 3. No safety backup should have been created (restore was skipped)
        try (java.util.stream.Stream<Path> stream = Files.list(checkpointsDir)) {
            assertEquals(0, stream.count(),
                    "No checkpoint should be created since restore was skipped");
        }

        // 4. Failure flag file MUST exist
        assertTrue(Files.exists(flagFile),
                "Restore failure flag must be written for the frontend alert");

        // 5. Flag content should contain diagnostic info
        String flagContent = Files.readString(flagFile);
        assertTrue(flagContent.contains("RESTORE_SKIPPED"), "Flag must indicate RESTORE_SKIPPED");
        assertTrue(flagContent.contains("size="), "Flag must contain the actual file size");
    }

    @Test
    void whenRestoreFileIsExactlyAtThreshold_shouldRestore() throws IOException {
        // GIVEN - a restore file at exactly the minimum threshold (boundary test)
        Files.writeString(dbFile, "OLD_DATA");
        byte[] thresholdData = new byte[(int) DatabaseRestorer.MIN_RESTORE_FILE_SIZE_BYTES];
        java.util.Arrays.fill(thresholdData, (byte) 'Y');
        Files.write(restoreFile, thresholdData);

        // WHEN
        DatabaseRestorer.checkAndRestore();

        // THEN - should proceed with restore (>= threshold)
        byte[] dbContent = Files.readAllBytes(dbFile);
        assertEquals(thresholdData.length, dbContent.length, "DB should be replaced at exact threshold");
        assertFalse(Files.exists(restoreFile), "Restore file should be consumed");
        assertFalse(Files.exists(flagFile), "No failure flag at exact threshold");
    }

    @Test
    void whenRestoreFileIsOneBelowThreshold_shouldSkip() throws IOException {
        // GIVEN - a restore file one byte below the threshold
        Files.writeString(dbFile, "KEEP_ME");
        byte[] tooSmallData = new byte[(int) DatabaseRestorer.MIN_RESTORE_FILE_SIZE_BYTES - 1];
        java.util.Arrays.fill(tooSmallData, (byte) 'Z');
        Files.write(restoreFile, tooSmallData);

        // WHEN
        DatabaseRestorer.checkAndRestore();

        // THEN
        assertEquals("KEEP_ME", Files.readString(dbFile), "DB must remain untouched");
        assertFalse(Files.exists(restoreFile), "Invalid file should be cleaned up");
        assertTrue(Files.exists(flagFile), "Flag file must be written");
    }
}
