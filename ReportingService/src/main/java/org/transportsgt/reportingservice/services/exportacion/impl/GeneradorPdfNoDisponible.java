package org.transportsgt.reportingservice.services.exportacion.impl;

import org.springframework.stereotype.Component;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.exception.FormatoNoSoportadoException;
import org.transportsgt.reportingservice.services.exportacion.GeneradorPdf;

/**
 * Provisional mientras no esté JasperReports: {@code formato=pdf} responde 400. Se reemplaza por
 * {@code GeneradorReportesJasper}.
 */
@Component
public class GeneradorPdfNoDisponible implements GeneradorPdf {

    @Override
    public byte[] generar(String plantilla, ReporteTabla<?> reporte) {
        throw new FormatoNoSoportadoException("pdf");
    }
}
