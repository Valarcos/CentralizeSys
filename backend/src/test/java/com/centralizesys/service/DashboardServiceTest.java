package com.centralizesys.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.centralizesys.model.dashboard.DashboardSummaryResponse;
import com.centralizesys.model.dto.BackupFileDTO;
import com.centralizesys.repository.AlertaChequeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private ProductService productService;

    @Mock
    private BackupService backupService;

    @Mock
    private DeudoresService deudoresService;

    @Mock
    private AlertaChequeRepository alertaChequeRepository;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    void getSummaryAggregatesSuccessfully() {
        LocalDateTime backupDate = LocalDateTime.of(2026, 10, 20, 10, 0, 0);
        BackupFileDTO backup = new BackupFileDTO("backup-2026-10-20.dump", backupDate, 1024L, "DB");
        when(productService.getLowStockAlerts()).thenReturn(Collections.emptyList());
        when(backupService.listBackups()).thenReturn(List.of(backup));
        when(deudoresService.hasActiveDebts()).thenReturn(true);
        when(alertaChequeRepository.findPendingExpiredOrToday()).thenReturn(Collections.emptyList());

        DashboardSummaryResponse summary = dashboardService.getSummary();

        assertNotNull(summary);
        assertTrue(summary.hasActiveDebts());
        assertNotNull(summary.lastBackup());
        assertEquals("backup-2026-10-20.dump", summary.lastBackup().filename());
        assertTrue(summary.stockAlerts().isEmpty());
        assertTrue(summary.pendingCheques().isEmpty());
        assertTrue(summary.systemAlerts().isEmpty());
    }

    @Test
    void getSummaryHandlesEmptyBackupsGracefully() {
        when(productService.getLowStockAlerts()).thenReturn(Collections.emptyList());
        when(backupService.listBackups()).thenReturn(Collections.emptyList());
        when(deudoresService.hasActiveDebts()).thenReturn(false);
        when(alertaChequeRepository.findPendingExpiredOrToday()).thenReturn(Collections.emptyList());

        DashboardSummaryResponse summary = dashboardService.getSummary();

        assertNotNull(summary);
        assertFalse(summary.hasActiveDebts());
        assertNull(summary.lastBackup());
        assertNotNull(summary.pendingCheques());
        assertTrue(summary.pendingCheques().isEmpty());
    }

    @Test
    void getSummaryFailsFastWhenProductServiceReturnsNull() {
        when(productService.getLowStockAlerts()).thenReturn(null);

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> dashboardService.getSummary()
        );

        assertTrue(exception.getMessage().contains("ProductService.getLowStockAlerts() devolvió null"));
    }

    @Test
    void getSummaryFailsFastWhenBackupServiceReturnsNull() {
        when(productService.getLowStockAlerts()).thenReturn(Collections.emptyList());
        when(backupService.listBackups()).thenReturn(null);

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> dashboardService.getSummary()
        );

        assertTrue(exception.getMessage().contains("BackupService.listBackups() devolvió null"));
    }

    @Test
    void getSummaryFailsFastWhenAlertaChequeRepositoryReturnsNull() {
        when(productService.getLowStockAlerts()).thenReturn(Collections.emptyList());
        when(backupService.listBackups()).thenReturn(Collections.emptyList());
        when(alertaChequeRepository.findPendingExpiredOrToday()).thenReturn(null);

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> dashboardService.getSummary()
        );

        assertTrue(exception.getMessage().contains("AlertaChequeRepository.findPendingExpiredOrToday() devolvió null"));
    }
}
