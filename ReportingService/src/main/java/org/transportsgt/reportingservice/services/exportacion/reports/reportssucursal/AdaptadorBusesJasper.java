package org.transportsgt.reportingservice.services.exportacion.reports.reportssucursal;

import org.springframework.stereotype.Component;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportssucursal.FilaBus;
import org.transportsgt.reportingservice.services.exportacion.reports.*;

import java.math.BigDecimal;
import java.util.List;

@Component
public class AdaptadorBusesJasper implements ReporteJasper {
    private final SoporteAdaptadoresJasper soporte;
    private final FormateadorReporteJasper formato;

    public AdaptadorBusesJasper(SoporteAdaptadoresJasper soporte, FormateadorReporteJasper formato) {
        this.soporte = soporte;
        this.formato = formato;
    }

    public String nombre() {
        return "buses";
    }

    public List<FilaReportePdf> adaptar(ReporteTabla<?> reporte) {
        return soporte.aplanar(reporte, FilaBus.class, fila -> new DatosFilaJasper(BigDecimal.ZERO,
                formato.decimal(fila.viajesRealizados()), fila.placa(), fila.marca() + " " + fila.modelo(),
                formato.texto(fila.capacidad()), fila.estadoOperativo(), formato.texto(fila.chofer()),
                formato.texto(fila.kilometrajeActual()) + " km", formato.texto(fila.viajesRealizados()),
                fila.activo() ? "Activo" : "Inactivo"));
    }
}
