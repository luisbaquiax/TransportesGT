package org.transportsgt.reportingservice.services.reportssucursal;

import org.transportsgt.reportingservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportssucursal.FilaDepreciacionBus;

import java.util.UUID;

/**
 * Reporte 11: depreciación acumulada por bus y tarifa vigente.
 * El ADMIN_SUCURSAL ve siempre su sucursal; el ADMIN_SISTEMA indica {@code idSucursal}.
 */
public interface ReporteDepreciacionBusesService {

    /** Nombre de la plantilla y del archivo PDF. */
    String NOMBRE = "depreciacion-buses";

    ReporteTabla<FilaDepreciacionBus> generar(UsuarioAutenticado usuario, UUID idSucursal, FiltroFechas intervalo);
}
