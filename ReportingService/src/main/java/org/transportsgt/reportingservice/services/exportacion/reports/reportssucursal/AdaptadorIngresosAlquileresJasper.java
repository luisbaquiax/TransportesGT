package org.transportsgt.reportingservice.services.exportacion.reports.reportssucursal;

import org.springframework.stereotype.Component;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportssucursal.FilaIngresoAlquiler;
import org.transportsgt.reportingservice.services.exportacion.reports.*;

import java.math.BigDecimal;
import java.util.List;

@Component
public class AdaptadorIngresosAlquileresJasper implements ReporteJasper {
    private final SoporteAdaptadoresJasper soporte;
    private final FormateadorReporteJasper formato;

    public AdaptadorIngresosAlquileresJasper(SoporteAdaptadoresJasper soporte, FormateadorReporteJasper formato) {
        this.soporte = soporte;
        this.formato = formato;
    }

    public String nombre() {
        return "ingresos-alquileres";
    }

    public List<FilaReportePdf> adaptar(ReporteTabla<?> reporte) {
        return soporte.aplanar(reporte, FilaIngresoAlquiler.class, fila -> new DatosFilaJasper(fila.precioTotal(), BigDecimal.ZERO,
                formato.texto(fila.cliente()) + " · NIT " + formato.texto(fila.nit()), fila.origen(), fila.destino(),
                formato.texto(fila.fechaSalida()), formato.texto(fila.fechaRetorno()), formato.texto(fila.placa()),
                formato.texto(fila.fechaPago()), formato.moneda(fila.precioTotal())));
    }
}
