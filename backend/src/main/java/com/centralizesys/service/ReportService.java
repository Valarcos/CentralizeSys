package com.centralizesys.service;

import com.centralizesys.repository.ReportRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    private final ReportRepository reportRepository;

    public ReportService(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    // --- LEGACY METHODS (preserved, still used by existing endpoints) ---

    public List<Map<String, Object>> getGananciasMensuales(int year) {
        return reportRepository.getGananciasMensuales(year);
    }

    public Map<String, Object> getGananciasMensuales(int year, int month) {
        return reportRepository.getGananciaPorMes(year, month);
    }

    // --- PHASE 5: UNIFIED STATISTICS ---

    /**
     * Returns unified statistics combining Commercial Revenue (accrual) and
     * Cash Flow (cash-basis) for the requested time granularity.
     *
     * @param year  The target year (required).
     * @param month The target month (1-12), or null for a full-year query.
     * @param day   The target day, or null for a monthly/yearly query.
     * @return ReportesEstadisticasDTO with the structured data.
     */
    public com.centralizesys.model.sales.ReportesEstadisticasDTO getEstadisticas(Integer year, Integer month, Integer day) {
        return reportRepository.getEstadisticas(year, month, day);
    }
}
