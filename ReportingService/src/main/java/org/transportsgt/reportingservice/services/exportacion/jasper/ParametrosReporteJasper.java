package org.transportsgt.reportingservice.services.exportacion.jasper;

import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ParametrosReporteJasper {
    private final DateTimeFormatter fechaHora = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public Map<String, Object> crear(ReporteTabla<?> reporte, PlantillaJasper plantilla) {
        Map<String, Object> parametros = new LinkedHashMap<>();
        parametros.put("TITULO", reporte.titulo());
        parametros.put("PERIODO", periodo(reporte));
        parametros.put("GENERADO_EN", reporte.generadoEn().format(fechaHora));
        parametros.put("FILTROS", reporte.filtros().otros().entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining(" · ")));
        parametros.put("TOTALES", reporte.totales().entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining(" · ")));
        for (int i = 0; i < 8; i++) {
            parametros.put("H" + (i + 1), i < plantilla.columnas().size() ? plantilla.columnas().get(i) : "");
        }
        return parametros;
    }

    private String periodo(ReporteTabla<?> reporte) {
        String desde = reporte.filtros().desde() == null ? "Inicio" : reporte.filtros().desde().toString();
        String hasta = reporte.filtros().hasta() == null ? "Hoy" : reporte.filtros().hasta().toString();
        if (reporte.filtros().desde() == null && reporte.filtros().hasta() == null) return "Todo el historial";
        return desde + " — " + hasta;
    }
}
