package org.transportsgt.reportingservice.services.exportacion.reports.reportsadmin;

import org.springframework.stereotype.Component;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaGasto;
import org.transportsgt.reportingservice.services.exportacion.reports.*;

import java.math.BigDecimal;
import java.util.List;

@Component
public class AdaptadorGastosJasper implements ReporteJasper {
    private final SoporteAdaptadoresJasper soporte;
    private final FormateadorReporteJasper formato;

    public AdaptadorGastosJasper(SoporteAdaptadoresJasper soporte, FormateadorReporteJasper formato) {
        this.soporte = soporte;
        this.formato = formato;
    }

    public String nombre() { return "gastos"; }

    public List<FilaReportePdf> adaptar(ReporteTabla<?> reporte) {
        return soporte.aplanar(reporte, FilaGasto.class, fila -> new DatosFilaJasper(fila.monto(), BigDecimal.ZERO,
                formato.texto(fila.fecha()), fila.tipo(), formato.texto(fila.referencia()), formato.texto(fila.placa()),
                fila.descripcion(), formato.moneda(fila.monto())));
    }
}
