package org.transportsgt.reportingservice.dto.response.reportsadmin;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Reporte 4: una ruta con sus boletos vendidos en el intervalo.
 *
 * @param eliminada la ruta ya no existe, pero tuvo ventas en el intervalo
 */
public record FilaRutaDemandada(
        UUID idRuta,
        String origen,
        String destino,
        BigDecimal distanciaKm,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal precioBoleto,
        long boletosVendidos,
        long viajesConVentas,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal ingreso,
        boolean eliminada
) {
}
