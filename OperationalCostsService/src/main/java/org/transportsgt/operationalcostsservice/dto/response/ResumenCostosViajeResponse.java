package org.transportsgt.operationalcostsservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ResumenCostosViajeResponse(
        UUID idViaje,
        CombustibleResponse combustible,
        DepreciacionResponse depreciacion,
        PagoChoferResponse pagoChofer
) {

    public record CombustibleResponse(
            UUID idGasto,
            UUID idBus,
            UUID idSucursal,
            BigDecimal monto,
            LocalDate fechaGasto
    ) {
    }

    public record DepreciacionResponse(
            UUID idDepreciacion,
            UUID idBus,
            UUID idSucursal,
            BigDecimal distanciaKm,
            BigDecimal montoPorKm,
            BigDecimal monto,
            LocalDate fechaCalculo
    ) {
    }

    public record PagoChoferResponse(
            UUID idPago,
            UUID idChofer,
            UUID idSucursal,
            BigDecimal salarioBase,
            BigDecimal multiplicador,
            BigDecimal monto,
            LocalDate fechaPago
    ) {
    }
}
