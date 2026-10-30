package org.transportsgt.reportingservice.services.exportacion.reports.reportsadmin;

import org.springframework.stereotype.Component;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaMapaRuta;
import org.transportsgt.reportingservice.services.exportacion.reports.*;

import java.util.List;

@Component
public class AdaptadorMapaRutasJasper implements ReporteJasper {
    private final SoporteAdaptadoresJasper soporte;
    private final FormateadorReporteJasper formato;

    public AdaptadorMapaRutasJasper(SoporteAdaptadoresJasper soporte, FormateadorReporteJasper formato) {
        this.soporte = soporte;
        this.formato = formato;
    }

    public String nombre() { return "mapa-rutas"; }

    public List<FilaReportePdf> adaptar(ReporteTabla<?> reporte) {
        return soporte.aplanar(reporte, FilaMapaRuta.class, fila -> new DatosFilaJasper(
                fila.precioBoleto(), fila.distanciaKm(), fila.origen(), formato.texto(fila.latitudOrigen()),
                formato.texto(fila.longitudOrigen()), fila.destino(), formato.texto(fila.latitudDestino()),
                formato.texto(fila.longitudDestino()), formato.texto(fila.distanciaKm()) + " km", formato.moneda(fila.precioBoleto())));
    }
}
