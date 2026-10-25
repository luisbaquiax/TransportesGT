package org.transportsgt.reportingservice.services.comun;

import org.springframework.stereotype.Component;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.request.FormatoReporte;
import org.transportsgt.reportingservice.exception.FormatoNoSoportadoException;
import org.transportsgt.reportingservice.exception.IntervaloInvalidoException;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Reglas comunes de los parámetros de todos los reportes.
 */
@Component
public class ValidadorFiltros {

    /** {@code desde} posterior a {@code hasta} → 400. Cualquiera de los dos puede faltar. */
    public FiltroFechas intervalo(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IntervaloInvalidoException();
        }
        return new FiltroFechas(desde, hasta);
    }

    /** Sin formato = JSON; solo se aceptan {@code json} y {@code pdf}. */
    public FormatoReporte formato(String formato) {
        if (formato == null || formato.isBlank()) {
            return FormatoReporte.JSON;
        }
        return switch (formato.trim().toLowerCase(Locale.ROOT)) {
            case "json" -> FormatoReporte.JSON;
            case "pdf" -> FormatoReporte.PDF;
            default -> throw new FormatoNoSoportadoException(formato);
        };
    }
}
