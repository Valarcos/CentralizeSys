package com.centralizesys.model.sales;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportesEstadisticasDTO {

    private RendimientoComercial rendimientoComercial;
    private FlujoDeCaja flujoDeCaja;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RendimientoComercial {
        private Double ingresosVentas;
        private Double costoTotalVendido;
        private Long productosVendidos;
        private Long productosComprados;
        private Double deudasPendientes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlujoDeCaja {
        private Double ingresosEfectivo;
        private Double egresosEfectivo;
        private Double balanceNeto;
    }
}
