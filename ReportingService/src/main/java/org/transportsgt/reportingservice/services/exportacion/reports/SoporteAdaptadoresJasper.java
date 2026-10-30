package org.transportsgt.reportingservice.services.exportacion.reports;

import org.transportsgt.reportingservice.dto.response.GrupoReporte;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Component
public class SoporteAdaptadoresJasper {

    public <F> List<FilaReportePdf> aplanar(ReporteTabla<?> reporte, Class<F> tipo,
                                            Function<F, DatosFilaJasper> convertir) {
        List<FilaReportePdf> resultado = new ArrayList<>();
        for (GrupoReporte<?> grupo : reporte.grupos()) {
            if (grupo.filas() != null) {
                agregar(resultado, grupo.titulo(), null, grupo.filas(), tipo, convertir);
            }
            if (grupo.subgrupos() != null) {
                for (GrupoReporte<?> subgrupo : grupo.subgrupos()) {
                    agregar(resultado, grupo.titulo(), subgrupo.titulo(), subgrupo.filas(), tipo, convertir);
                }
            }
        }
        return resultado;
    }

    private <F> void agregar(List<FilaReportePdf> destino, String grupo1, String grupo2,
                             List<?> filas, Class<F> tipo, Function<F, DatosFilaJasper> convertir) {
        if (filas == null) return;
        for (Object valor : filas) {
            DatosFilaJasper fila = convertir.apply(tipo.cast(valor));
            destino.add(new FilaReportePdf(grupo1, grupo2, fila.columnas(), fila.numero1(), fila.numero2()));
        }
    }

}
