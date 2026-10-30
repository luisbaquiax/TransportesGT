package org.transportsgt.reportingservice.services.exportacion.reports.reportsadmin;

import org.springframework.stereotype.Component;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaRutaDemandada;
import org.transportsgt.reportingservice.services.exportacion.reports.*;

import java.util.List;

@Component
public class AdaptadorRutasDemandadasJasper implements ReporteJasper {
    private final SoporteAdaptadoresJasper soporte;
    private final FormateadorReporteJasper formato;

    public AdaptadorRutasDemandadasJasper(SoporteAdaptadoresJasper soporte, FormateadorReporteJasper formato) {
        this.soporte = soporte;
        this.formato = formato;
    }

    public String nombre() { return "rutas-demandadas"; }

    public List<FilaReportePdf> adaptar(ReporteTabla<?> reporte) {
        return soporte.aplanar(reporte, FilaRutaDemandada.class, fila -> new DatosFilaJasper(
                fila.ingreso(), formato.decimal(fila.boletosVendidos()), fila.origen(), fila.destino(),
                formato.texto(fila.distanciaKm()) + " km", formato.moneda(fila.precioBoleto()),
                formato.texto(fila.boletosVendidos()), formato.texto(fila.viajesConVentas()),
                formato.moneda(fila.ingreso()), fila.eliminada() ? "Eliminada" : "Activa"));
    }
}
