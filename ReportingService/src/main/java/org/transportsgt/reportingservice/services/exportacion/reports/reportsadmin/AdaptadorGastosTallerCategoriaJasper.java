package org.transportsgt.reportingservice.services.exportacion.reports.reportsadmin;

import org.springframework.stereotype.Component;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.services.exportacion.reports.*;

import java.util.List;

@Component
public class AdaptadorGastosTallerCategoriaJasper implements ReporteJasper {
    private final ConversorFilasTallerJasper conversor;

    public AdaptadorGastosTallerCategoriaJasper(ConversorFilasTallerJasper conversor) {
        this.conversor = conversor;
    }

    public String nombre() {
        return "gastos-taller-por-categoria";
    }

    public List<FilaReportePdf> adaptar(ReporteTabla<?> reporte) {
        return conversor.convertir(reporte);
    }
}
