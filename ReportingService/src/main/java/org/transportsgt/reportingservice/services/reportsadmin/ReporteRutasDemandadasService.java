package org.transportsgt.reportingservice.services.reportsadmin;

import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaRutaDemandada;

/**
 * Reporte 4 (ADMIN_SISTEMA): rutas de mayor a menor por boletos vendidos.
 */
public interface ReporteRutasDemandadasService {

    /** Nombre de la plantilla y del archivo PDF. */
    String NOMBRE = "rutas-demandadas";

    ReporteTabla<FilaRutaDemandada> generar(FiltroFechas intervalo);
}
