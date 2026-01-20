package com.centralizesys.service;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.debt.DeudaResponse;
import com.centralizesys.model.enums.DebtStatus;
import com.centralizesys.model.sales.Venta;
import com.centralizesys.repository.DeudoresRepository;
import com.centralizesys.repository.VentaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DeudoresServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DeudoresService deudoresService;

    @Autowired
    private DeudoresRepository deudoresRepository;

    @Autowired
    private VentaRepository ventaRepository;

    @Test
    @DisplayName("Should handle partial and full payments with correct Double precision")
    void shouldHandlePaymentsRefactor() {
        // 1. Setup Data
        Long userId = createTestUser();

        Venta venta = new Venta();
        venta.setFecha(LocalDate.now().toString());
        venta.setClienteNombre("Test Debtor");
        venta.setTotalVenta(100.0);
        venta.setUsuarioId(userId);
        Long ventaId = ventaRepository.saveVenta(venta);

        // Create Debt of $100.50
        deudoresRepository.save(ventaId, "Test Debtor", 100.50);

        // Retrieve ID of the created debt
        DeudaResponse initialDebt = deudoresRepository.findAll().stream()
                .filter(d -> d.getVentaId().equals(ventaId))
                .findFirst()
                .orElseThrow();
        Long deudaId = initialDebt.getId();

        assertEquals(DebtStatus.PENDIENTE.name(), initialDebt.getEstado());

        // 2. Partial Payment ($50.20)
        // Expected Balance: 100.50 - 50.20 = 50.30
        DeudaResponse partial = deudoresService.registrarPago(deudaId, 50.20, userId);

        assertEquals(50.30, partial.getMontoDeuda(), 0.001, "Balance should be 50.30");
        assertEquals(DebtStatus.PARCIAL.name(), partial.getEstado());

        // 3. Full Payment ($50.30)
        // Expected Balance: 0.00
        DeudaResponse full = deudoresService.registrarPago(deudaId, 50.30, userId);

        assertEquals(0.00, full.getMontoDeuda(), 0.001);
        assertEquals(DebtStatus.PAGADO.name(), full.getEstado());
    }

    @Test
    @DisplayName("Should handle tiny rounding issues gracefully")
    void shouldHandleRounding() {
        // Scenario: 10.00 debt. Payment of 3.33 repeated 3 times.
        // 10.00 - 3.33 = 6.67
        // 6.67 - 3.33 = 3.34
        // 3.34 - 3.34 = 0.00 (Last payment adjusts)

        Long userId = createTestUser();
        Venta venta = new Venta(null, LocalDate.now().toString(), "Math User", 10.0, userId);
        Long ventaId = ventaRepository.saveVenta(venta);

        deudoresRepository.save(ventaId, "Math User", 10.00);
        Long deudaId = deudoresRepository.findAll().get(0).getId();

        // Pay 1: 3.33
        DeudaResponse r1 = deudoresService.registrarPago(deudaId, 3.33, userId);
        assertEquals(6.67, r1.getMontoDeuda());

        // Pay 2: 3.33
        DeudaResponse r2 = deudoresService.registrarPago(deudaId, 3.33, userId);
        assertEquals(3.34, r2.getMontoDeuda());
        // Note: 6.67 - 3.33 = 3.34 exactly in double, but we verify our rounding holds.

        // Pay 3: 3.34 (Clean finish)
        DeudaResponse r3 = deudoresService.registrarPago(deudaId, 3.34, userId);
        assertEquals(0.00, r3.getMontoDeuda());
        assertEquals(DebtStatus.PAGADO.name(), r3.getEstado());
    }
}
