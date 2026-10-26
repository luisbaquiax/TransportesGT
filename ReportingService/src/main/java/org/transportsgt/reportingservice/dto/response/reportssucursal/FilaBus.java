package org.transportsgt.reportingservice.dto.response.reportssucursal;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Reporte 7: un bus de la sucursal.
 *
 * @param chofer chofer del viaje en tránsito o del próximo programado; {@code null} si no tiene
 */
public record FilaBus(
        UUID idBus,
        String placa,
        String marca,
        String modelo,
        Integer capacidad,
        String estadoOperativo,
        String chofer,
        BigDecimal kilometrajeActual,
        long viajesRealizados,
        boolean activo
) {
}
