package org.transportsgt.reportingservice.services.reportsadmin;

import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaGastoTaller;

import java.util.UUID;

/**
 * Reporte 5 (ADMIN_SISTEMA): gastos de taller por categoría, de mayor a menor gasto.
 */
public interface ReporteGastosTallerCategoriaService {

    /** Nombre de la plantilla y del archivo PDF. */
    String NOMBRE = "gastos-taller-por-categoria";

    ReporteTabla<FilaGastoTaller> generar(FiltroFechas intervalo, UUID idSucursal, UUID idBus);
}
