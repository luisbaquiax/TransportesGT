package org.transportsgt.reportingservice.services.reportsadmin;

import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaGanancia;

import java.util.UUID;

/**
 * Reporte 3 (ADMIN_SISTEMA): ingresos, gastos y ganancia neta por sucursal, en orden alfabético.
 */
public interface ReporteGananciasService {

    /** Nombre de la plantilla y del archivo PDF. */
    String NOMBRE = "ganancias";

    ReporteTabla<FilaGanancia> generar(FiltroFechas intervalo, UUID idSucursal);
}
