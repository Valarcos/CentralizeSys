package com.centralizesys.service;

import com.centralizesys.repository.ProductRepository;
import com.centralizesys.repository.VentaRepository;
import com.centralizesys.repository.CompraRepository;
import com.centralizesys.repository.DeudoresRepository;
import com.centralizesys.repository.AuditoriaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackupServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private AuditoriaService auditoriaService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private VentaRepository ventaRepository;

    @Mock
    private CompraRepository compraRepository;

    @Mock
    private DeudoresRepository deudoresRepository;

    @Mock
    private AuditoriaRepository auditoriaRepository;

    // We can't easily mock the final ExcelService/Internal logic yet, so we'll
    // structure the service
    // to use dependencies we can mock.

    @Test
    @DisplayName("performBackup executes VACUUM INTO and audits success")
    void performBackup_Success() {
        BackupService service = new BackupService(jdbcTemplate, auditoriaService, productRepository, ventaRepository,
                compraRepository, deudoresRepository, auditoriaRepository);

        service.performBackup(BackupService.BackupType.MANUAL, 1L);

        // 1. Verify VACUUM INTO was called
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(sqlCaptor.capture());
        String executedSql = sqlCaptor.getValue();

        // Null check to satisfy linter
        if (executedSql == null)
            executedSql = "";

        assertTrue(executedSql.toUpperCase().startsWith("VACUUM INTO"));
        // Check for manual path
        assertTrue(executedSql.replace("\\", "/").contains("backups/manual"));

        // 2. Verify Audit Success
        verify(auditoriaService).registrarAccion(eq(1L), eq("BACKUP_EXITOSO"), contains("MANUAL"));

        // 3. Verify Excel File Creation
        // (Optional: Verify file exists in backups/manual, but integration test does
        // this better.
        // Here we mainly test the flow logic).
        File dir = new File("backups/manual");
        if (dir.exists()) {
            File[] excelFiles = dir
                    .listFiles((d, name) -> name.startsWith("centralizesys_manual_") && name.endsWith(".xlsx"));
            // Cleanup
            if (excelFiles != null) {
                for (File f : excelFiles) {
                    boolean deleted = f.delete();
                    assertTrue(deleted, "Failed to delete test file: " + f.getName());
                }
            }
        }
    }

    @Test
    @DisplayName("performBackup audits failure if SQL throws exception")
    void performBackup_Failure_AuditsError() {
        BackupService service = new BackupService(jdbcTemplate, auditoriaService, productRepository, ventaRepository,
                compraRepository, deudoresRepository, auditoriaRepository);

        // Force SQL Exception (DataAccessException is unchecked, use concrete class)
        doThrow(new org.springframework.dao.DataIntegrityViolationException("Disk Full")).when(jdbcTemplate)
                .execute(anyString());

        // Verify Audit Failure
        assertThrows(com.centralizesys.exception.InfrastructureException.class,
                () -> service.performBackup(BackupService.BackupType.MANUAL, 1L));
        verify(auditoriaService).registrarAccion(eq(1L), eq("BACKUP_FALLIDO"), contains("Disk Full"));
    }
}
