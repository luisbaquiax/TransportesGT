package org.transportsgt.reportingservice.services.reportsadmin;

import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaIngreso;

import java.util.UUID;

/**
 * Reporte 1 (ADMIN_SISTEMA): ingresos agrupados por sucursal, con subtotal por sucursal y total global.
 */
public interface ReporteIngresosService {

    /** Nombre de la plantilla y del archivo PDF. */
    String NOMBRE = "ingresos";

    ReporteTabla<FilaIngreso> generar(FiltroFechas intervalo, UUID idSucursal);
}
