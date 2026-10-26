package org.transportsgt.reportingservice.services.reportsadmin;

import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaMapaRuta;

import java.util.UUID;

/**
 * Reporte 6 (ADMIN_SISTEMA): rutas con origen en la sucursal, con coordenadas.
 */
public interface ReporteMapaRutasService {

    /** Nombre de la plantilla y del archivo PDF. */
    String NOMBRE = "mapa-rutas";

    ReporteTabla<FilaMapaRuta> generar(FiltroFechas intervalo, UUID idSucursal);
}
