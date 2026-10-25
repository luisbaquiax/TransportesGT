package org.transportsgt.reportingservice.services.comun;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.transportsgt.reportingservice.dto.request.FormatoReporte;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.services.exportacion.GeneradorPdf;
import org.transportsgt.reportingservice.utils.Fechas;

/**
 * Devuelve el mismo {@link ReporteTabla} como JSON o como PDF descargable.
 */
@Component
@RequiredArgsConstructor
public class RespuestaReporte {

    private final GeneradorPdf generadorPdf;
    private final Fechas fechas;

    /**
     * @param nombre nombre corto del reporte: plantilla y archivo {@code reporte-<nombre>-<fecha>.pdf}
     */
    public ResponseEntity<?> responder(ReporteTabla<?> reporte, FormatoReporte formato, String nombre) {
        if (formato == FormatoReporte.JSON) {
            return ResponseEntity.ok(reporte);
        }
        byte[] pdf = generadorPdf.generar(nombre, reporte);
        String archivo = "reporte-" + nombre + "-" + fechas.hoy() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(archivo).build().toString())
                .body(pdf);
    }
}
