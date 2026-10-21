package com.centralizesys.service;

import com.centralizesys.model.cheque.AlertaCheque;
import com.centralizesys.model.dashboard.DashboardSummaryResponse;
import com.centralizesys.model.dto.BackupFileDTO;
import com.centralizesys.model.product.Product;
import com.centralizesys.model.product.ProductResponse;
import com.centralizesys.repository.AlertaChequeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final ProductService productService;
    private final BackupService backupService;
    private final DeudoresService deudoresService;
    private final AlertaChequeRepository alertaChequeRepository;

    public DashboardService(ProductService productService,
                            BackupService backupService,
                            DeudoresService deudoresService,
                            AlertaChequeRepository alertaChequeRepository) {
        this.productService = productService;
        this.backupService = backupService;
        this.deudoresService = deudoresService;
        this.alertaChequeRepository = alertaChequeRepository;
    }

    public DashboardSummaryResponse getSummary() {
        // 1. Negative stock alerts - FAIL-FAST: Throws NPE with descriptive message if the service violates its contract
        List<Product> lowStock = Objects.requireNonNull(
                productService.getLowStockAlerts(),
                "ProductService.getLowStockAlerts() devolvió null (violación de contrato de servicio, Fail-Fast GEMINI.md §4)"
        );
        List<ProductResponse> stockAlerts = lowStock.stream()
                .map(ProductResponse::new)
                .toList();

        // 2. Most recent backup metadata (or null if none exists) - FAIL-FAST on null collection
        List<BackupFileDTO> backups = Objects.requireNonNull(
                backupService.listBackups(),
                "BackupService.listBackups() devolvió null (violación de contrato de servicio, Fail-Fast GEMINI.md §4)"
        );
        BackupFileDTO lastBackup = backups.isEmpty() ? null : backups.getFirst();

        // 3. Active debtor reminder flag
        boolean hasActiveDebts = deudoresService.hasActiveDebts();

        // 4. Pending / due cheques for today or overdue - FAIL-FAST on null collection
        List<AlertaCheque> pendingCheques = Objects.requireNonNull(
                alertaChequeRepository.findPendingExpiredOrToday(),
                "AlertaChequeRepository.findPendingExpiredOrToday() devolvió null (violación de contrato de servicio, Fail-Fast GEMINI.md §4)"
        );

        // 5. System alerts (strongly-typed DTO, reserved for infrastructure incidents / restore warnings)
        return new DashboardSummaryResponse(
                stockAlerts,
                lastBackup,
                hasActiveDebts,
                pendingCheques,
                Collections.emptyList()
        );
    }
}
