package org.transportsgt.reportingservice.services.exportacion.reports.reportssucursal;

import org.springframework.stereotype.Component;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.services.exportacion.reports.*;

import java.util.List;

@Component
public class AdaptadorGastosTallerBusJasper implements ReporteJasper {
    private final ConversorFilasTallerJasper conversor;

    public AdaptadorGastosTallerBusJasper(ConversorFilasTallerJasper conversor) {
        this.conversor = conversor;
    }

    public String nombre() {
        return "gastos-taller-por-bus";
    }

    public List<FilaReportePdf> adaptar(ReporteTabla<?> reporte) {
        return conversor.convertir(reporte);
    }
}
