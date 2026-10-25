package org.transportsgt.reportingservice.services.reportsadmin;

import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaGasto;

import java.util.UUID;

/**
 * Reporte 2 (ADMIN_SISTEMA): gastos agrupados por sucursal, con subtotal y desglose por tipo, y total global.
 */
public interface ReporteGastosService {

    /** Nombre de la plantilla y del archivo PDF. */
    String NOMBRE = "gastos";

    ReporteTabla<FilaGasto> generar(FiltroFechas intervalo, UUID idSucursal);
}
