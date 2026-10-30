package org.transportsgt.reportingservice.services.exportacion.reports.reportssucursal;

import org.springframework.stereotype.Component;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportssucursal.FilaIngresoBoleto;
import org.transportsgt.reportingservice.services.exportacion.reports.*;

import java.util.List;

@Component
public class AdaptadorIngresosBoletosJasper implements ReporteJasper {
    private final SoporteAdaptadoresJasper soporte;
    private final FormateadorReporteJasper formato;

    public AdaptadorIngresosBoletosJasper(SoporteAdaptadoresJasper soporte, FormateadorReporteJasper formato) {
        this.soporte = soporte;
        this.formato = formato;
    }

    public String nombre() {
        return "ingresos-boletos";
    }

    public List<FilaReportePdf> adaptar(ReporteTabla<?> reporte) {
        return soporte.aplanar(reporte, FilaIngresoBoleto.class, fila -> new DatosFilaJasper(fila.ingreso(),
                formato.decimal(fila.boletosVendidos()), fila.ruta(), formato.texto(fila.fechaHoraSalida()),
                formato.texto(fila.placa()), formato.texto(fila.boletosVendidos()), formato.moneda(fila.ingreso())));
    }
}
