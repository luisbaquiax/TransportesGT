package org.transportsgt.reportingservice.services.exportacion.reports.reportssucursal;

import org.springframework.stereotype.Component;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportssucursal.FilaDepreciacionBus;
import org.transportsgt.reportingservice.services.exportacion.reports.*;

import java.util.List;

@Component
public class AdaptadorDepreciacionBusesJasper implements ReporteJasper {
    private final SoporteAdaptadoresJasper soporte;
    private final FormateadorReporteJasper formato;

    public AdaptadorDepreciacionBusesJasper(SoporteAdaptadoresJasper soporte, FormateadorReporteJasper formato) {
        this.soporte = soporte;
        this.formato = formato;
    }

    public String nombre() {
        return "depreciacion-buses";
    }

    public List<FilaReportePdf> adaptar(ReporteTabla<?> reporte) {
        return soporte.aplanar(reporte, FilaDepreciacionBus.class, fila -> new DatosFilaJasper(
                fila.depreciacionAcumulada(), fila.kmRecorridos(), fila.placa(), formato.texto(fila.kmRecorridos()) + " km",
                formato.moneda(fila.montoPorKmActual()), formato.moneda(fila.depreciacionAcumulada()), formato.moneda(fila.promedioPorKm())));
    }
}
