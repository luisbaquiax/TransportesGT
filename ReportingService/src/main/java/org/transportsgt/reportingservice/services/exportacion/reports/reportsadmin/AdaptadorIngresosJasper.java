package org.transportsgt.reportingservice.services.exportacion.reports.reportsadmin;

import org.springframework.stereotype.Component;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaIngreso;
import org.transportsgt.reportingservice.services.exportacion.reports.*;

import java.util.List;

@Component
public class AdaptadorIngresosJasper implements ReporteJasper {
    private final SoporteAdaptadoresJasper soporte;
    private final FormateadorReporteJasper formato;

    public AdaptadorIngresosJasper(SoporteAdaptadoresJasper soporte, FormateadorReporteJasper formato) {
        this.soporte = soporte;
        this.formato = formato;
    }

    public String nombre() { return "ingresos"; }

    public List<FilaReportePdf> adaptar(ReporteTabla<?> reporte) {
        return soporte.aplanar(reporte, FilaIngreso.class, fila -> new DatosFilaJasper(
                fila.monto(), formato.decimal(fila.cantidadBoletos()), formato.texto(fila.fecha()), fila.tipo(),
                formato.texto(fila.referencia()), fila.descripcion(), formato.texto(fila.cliente()) + " · NIT " + formato.texto(fila.nit()),
                formato.texto(fila.cantidadBoletos()), formato.texto(fila.canal()), formato.moneda(fila.monto())));
    }
}
