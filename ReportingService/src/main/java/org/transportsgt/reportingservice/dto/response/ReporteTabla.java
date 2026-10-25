package org.transportsgt.reportingservice.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Modelo intermedio de todo reporte: lo usan la respuesta JSON y el PDF, así siempre coinciden.
 *
 * @param titulo     título del reporte
 * @param filtros    filtros aplicados (con nombres legibles)
 * @param generadoEn fecha y hora de generación en America/Guatemala
 * @param grupos     grupos en orden de presentación; los reportes sin agrupación traen un solo grupo sin clave
 * @param totales    totales del reporte; el dinero viaja como texto con 2 decimales
 * @param <F>        tipo de fila
 */
public record ReporteTabla<F>(
        String titulo,
        FiltrosAplicados filtros,
        OffsetDateTime generadoEn,
        List<GrupoReporte<F>> grupos,
        Map<String, Object> totales
) {
}
