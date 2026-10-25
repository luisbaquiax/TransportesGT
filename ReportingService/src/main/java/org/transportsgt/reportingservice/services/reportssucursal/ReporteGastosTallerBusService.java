package org.transportsgt.reportingservice.services.reportssucursal;

import org.transportsgt.reportingservice.config.security.UsuarioAutenticado;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaGastoTaller;

import java.util.UUID;

/**
 * Reporte 12: gastos de taller por bus agrupados por categoría, buses de mayor a menor gasto.
 * El ADMIN_SUCURSAL ve siempre su sucursal; el ADMIN_SISTEMA indica {@code idSucursal}.
 */
public interface ReporteGastosTallerBusService {

    /** Nombre de la plantilla y del archivo PDF. */
    String NOMBRE = "gastos-taller-por-bus";

    ReporteTabla<FilaGastoTaller> generar(UsuarioAutenticado usuario, UUID idSucursal, FiltroFechas intervalo, UUID idBus);
}
