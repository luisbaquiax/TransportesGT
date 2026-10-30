package org.transportsgt.reportingservice.services.exportacion.reports.reportssucursal;

import org.springframework.stereotype.Component;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportssucursal.FilaChofer;
import org.transportsgt.reportingservice.services.exportacion.reports.*;

import java.math.BigDecimal;
import java.util.List;

@Component
public class AdaptadorChoferesJasper implements ReporteJasper {
    private final SoporteAdaptadoresJasper soporte;
    private final FormateadorReporteJasper formato;

    public AdaptadorChoferesJasper(SoporteAdaptadoresJasper soporte, FormateadorReporteJasper formato) {
        this.soporte = soporte;
        this.formato = formato;
    }

    public String nombre() {
        return "choferes";
    }

    public List<FilaReportePdf> adaptar(ReporteTabla<?> reporte) {
        return soporte.aplanar(reporte, FilaChofer.class, fila -> new DatosFilaJasper(BigDecimal.ZERO,
                formato.decimal(fila.viajesRealizados()), fila.numeroLicencia(), fila.nombre(), fila.tipoLicencia(),
                formato.texto(fila.fechaVencimiento()), fila.estadoLicencia(), fila.activo() ? "Activo" : "Inactivo",
                formato.texto(fila.viajesRealizados())));
    }
}
