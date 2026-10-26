package org.transportsgt.reportingservice.services.exportacion;

import org.transportsgt.reportingservice.dto.response.ReporteTabla;

/**
 * Convierte un {@link ReporteTabla} en PDF con la plantilla del reporte.
 */
public interface GeneradorPdf {

    /**
     * @param plantilla nombre de la plantilla (p. ej. {@code ingresos})
     * @param reporte   los mismos datos de la respuesta JSON
     * @return bytes del PDF
     */
    byte[] generar(String plantilla, ReporteTabla<?> reporte);
}
