package org.transportsgt.reportingservice.dto.request;

import java.time.LocalDate;

/**
 * Intervalo de fechas de negocio, inclusivo. Sin fechas = todos los registros.
 */
public record FiltroFechas(
        LocalDate desde,
        LocalDate hasta
) {
}
