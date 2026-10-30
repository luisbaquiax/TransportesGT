package org.transportsgt.reportingservice.services.exportacion.reports.reportsadmin;

import org.springframework.stereotype.Component;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaGanancia;
import org.transportsgt.reportingservice.services.exportacion.reports.*;

import java.util.List;

@Component
public class AdaptadorGananciasJasper implements ReporteJasper {
    private final SoporteAdaptadoresJasper soporte;
    private final FormateadorReporteJasper formato;

    public AdaptadorGananciasJasper(SoporteAdaptadoresJasper soporte, FormateadorReporteJasper formato) {
        this.soporte = soporte;
        this.formato = formato;
    }

    public String nombre() { return "ganancias"; }

    public List<FilaReportePdf> adaptar(ReporteTabla<?> reporte) {
        return soporte.aplanar(reporte, FilaGanancia.class, fila -> new DatosFilaJasper(fila.ganancia(), fila.ingresos(),
                fila.sucursal(), formato.moneda(fila.ingresos()), formato.moneda(fila.gastos()), formato.moneda(fila.ganancia())));
    }
}
