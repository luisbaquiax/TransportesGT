package org.transportsgt.reportingservice.services.exportacion.reports;

import org.transportsgt.reportingservice.dto.response.ReporteTabla;

import java.util.List;

/**
 * Convierte un ReporteTabla ya calculado en la lista que recibe Jasper.
 */
public interface ReporteJasper {
    String nombre();

    List<FilaReportePdf> adaptar(ReporteTabla<?> reporte);
}
