package org.transportsgt.reportingservice.dto.response;

import java.time.LocalDate;
import java.util.Map;

/**
 * Filtros con los que se generó el reporte. Sin {@code desde} ni {@code hasta} = todos los registros.
 *
 * @param desde fecha de negocio inicial (inclusive)
 * @param hasta fecha de negocio final (inclusive)
 * @param otros filtros adicionales con su valor legible (p. ej. {@code "sucursal" → "Quetzaltenango"})
 */
public record FiltrosAplicados(
        LocalDate desde,
        LocalDate hasta,
        Map<String, String> otros
) {
}
