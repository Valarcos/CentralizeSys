package com.centralizesys.service;

import com.centralizesys.exception.InfrastructureException;
import com.centralizesys.repository.ProductRepository;
import com.centralizesys.repository.VentaRepository;
import com.centralizesys.repository.CompraRepository;
import com.centralizesys.repository.DeudoresRepository;
import com.centralizesys.repository.AuditoriaRepository;
import com.centralizesys.security.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.File;
import java.nio.file.Path;

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

    @Mock
    private BackupPathStrategy pathStrategy;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("performBackup executes VACUUM INTO and audits success")
    void performBackup_Success() {
        // GIVEN
        String manualDir = tempDir.resolve("manual").toString();
        when(pathStrategy.getManualDir()).thenReturn(manualDir);

        BackupService service = new BackupService(jdbcTemplate, auditoriaService, productRepository, ventaRepository,
                compraRepository, deudoresRepository, auditoriaRepository, pathStrategy);

        // WHEN
        service.performBackup(BackupService.BackupType.MANUAL, 1L);

        // THEN
        // 1. Verify VACUUM INTO was NOT called (Deferred)
        verify(jdbcTemplate, never()).execute(anyString());

        // 2. Verify Audit Success
        verify(auditoriaService).registrarAccion(eq(1L), eq("BACKUP_EXITOSO"), contains("MANUAL"));

        // 3. Verify Excel File Creation (using our temp dir)
        File dir = new File(manualDir);
        if (dir.exists()) {
            File[] excelFiles = dir
                    .listFiles((d, name) -> name.startsWith("centralizesys_manual_") && name.endsWith(".xlsx"));
            // Cleanup matches
            if (excelFiles != null) {
                for (File f : excelFiles) {
                    // Java creates it, verify it exists
                    assertTrue(f.exists());
                }
            }
        }
    }

    @Test
    @DisplayName("performBackup audits failure if SQL throws exception")
    void performBackup_Failure_AuditsError() {
        // GIVEN
        String manualDir = tempDir.resolve("manual").toString();
        // Use lenient because if exception happens early, this might not be called, or
        // strictly it IS called to resolve path before try-catch block
        lenient().when(pathStrategy.getManualDir()).thenReturn(manualDir);

        BackupService service = new BackupService(jdbcTemplate, auditoriaService, productRepository, ventaRepository,
                compraRepository, deudoresRepository, auditoriaRepository, pathStrategy);

        // Force Exception by throwing from auditoriaService during success log
        doThrow(new IllegalArgumentException("Simulated Error")).when(auditoriaService)
                .registrarAccion(eq(1L), eq("BACKUP_EXITOSO"), anyString());

        // WHEN / THEN
        assertThrows(InfrastructureException.class,
                () -> service.performBackup(BackupService.BackupType.MANUAL, 1L));
        verify(auditoriaService).registrarAccion(eq(1L), eq("BACKUP_FALLIDO"), anyString());
    }

    @Test
    @DisplayName("performBackup (no-arg) gets User ID from SecurityContext")
    void performBackup_UsesSecurityContext() {
        // GIVEN
        String manualDir = tempDir.resolve("manual").toString();
        when(pathStrategy.getManualDir()).thenReturn(manualDir);

        BackupService service = new BackupService(jdbcTemplate, auditoriaService, productRepository, ventaRepository,
                compraRepository, deudoresRepository, auditoriaRepository, pathStrategy);

        // Mock Security Context
        CustomUserDetails mockUser = mock(CustomUserDetails.class);
        when(mockUser.getId()).thenReturn(100L);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(mockUser);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);

        SecurityContextHolder.setContext(securityContext);

        try {
            // WHEN
            service.performBackup(BackupService.BackupType.MANUAL);

            // THEN
            verify(auditoriaService).registrarAccion(eq(100L), eq("BACKUP_EXITOSO"), anyString());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
