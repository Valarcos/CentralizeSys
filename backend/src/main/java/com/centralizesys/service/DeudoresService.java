package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.debt.DeudaResponse;
import com.centralizesys.model.debt.PagoDeuda;
import com.centralizesys.model.debt.PagoDeudaRequest; // NEW
import com.centralizesys.model.enums.DebtStatus; // Using Enum
import com.centralizesys.repository.DeudoresRepository;
import com.centralizesys.util.Constants;// Using Constants
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DeudoresService {

    private final DeudoresRepository repository;
    private final AuditoriaService auditoriaService;

    public DeudoresService(DeudoresRepository repository, AuditoriaService auditoriaService) {
        this.repository = repository;
        this.auditoriaService = auditoriaService;
    }

    public List<DeudaResponse> getAll() {
        return repository.findAll();
    }

    @Transactional
    public DeudaResponse registrarPago(Long id, List<PagoDeudaRequest> pagos, Long usuarioId) {
        if (pagos == null || pagos.isEmpty()) {
            throw new BusinessRuleException("Debe ingresar al menos un pago.");
        }

        // 1. Calculate Total Payment
        double totalPago = pagos.stream()
                .mapToDouble(PagoDeudaRequest::getMontoPago)
                .sum();

        if (totalPago <= 0) {
            throw new BusinessRuleException(Constants.ERR_PAYMENT_NEGATIVE);
        }

        // --- OPTIONAL USAGE EXPLANATION ---
        DeudaResponse deuda = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(Constants.ERR_DEBT_NOT_FOUND, id));

        // 2. Update Balance
        // We use Math.round to ensure 2 decimal precision without BigDecimal
        double saldoActual = deuda.getMontoDeuda();
        double rawNewBalance = saldoActual - totalPago;
        double saldoFinal = Math.round(rawNewBalance * 100.0) / 100.0;

        // 3. DYNAMIC STATE RECALCULATION
        DebtStatus nuevoEstado = calculateStatus(saldoFinal);

        // 4. Persist Updates
        repository.updateMontoAndEstado(id, saldoFinal, nuevoEstado.name());

        // 5. Persist History (One record per method)
        for (PagoDeudaRequest pago : pagos) {
            if (pago.getMontoPago() > 0) {
                repository.insertarPagoDeuda(id, pago.getMetodoPagoId(), pago.getMontoPago(), pago.getObservaciones(),
                        usuarioId);
            }
        }

        // 6. Update Object to return
        deuda.setMontoDeuda(saldoFinal);
        deuda.setEstado(nuevoEstado.name());

        // 7. Audit Log
        auditoriaService.registrarAccion(usuarioId, "PAGO_DEUDA",
                "Registrado pago de $" + totalPago + " (" + pagos.size() + " medios) para deuda ID " + id + " (" +
                        deuda.getClienteNombre() + ")");

        return deuda;
    }

    /**
     * Helper to determine status based purely on the remaining money.
     * This ensures the DB state never gets "stuck" in PAGADO if money is still
     * owed.
     */
    private DebtStatus calculateStatus(Double currentDebt) {
        // Floating point safety check (0.01 margin)
        if (currentDebt <= 0.01) {
            return DebtStatus.PAGADO;
        } else {
            // If we had the original total, we could distinguish PENDIENTE (0 paid)
            // vs PARCIAL (some paid), but for now, anything > 0 is pending/partial.
            // We default to PARCIAL as it implies "Not yet paid".
            // You can add logic here: if currentDebt == originalDebt -> PENDIENTE.
            return DebtStatus.PARCIAL;
        }
    }

    /**
     * Check if there are any active (non-PAGADO) debts.
     * Used by frontend for 15-day reminder badge.
     */
    public boolean hasActiveDebts() {
        return repository.hasActiveDebts();
    }

    /**
     * Get debts that have been pending/partial for more than X days.
     * Default to 15 days if not specified.
     */
    public List<DeudaResponse> getExpiredDebts() {
        // Hardcoded to 15 days for now, as implied by "missing api/deudores/expired"
        // and the "15-day reminder badge" context.
        return repository.findExpiredDebts(15);
    }

    public List<PagoDeuda> getPagos(Long id) {
        return repository.getPagosByDeudaId(id);
    }
}