package org.transportsgt.reportingservice.services.reportssucursal;

import org.transportsgt.reportingservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportssucursal.FilaChofer;

import java.util.UUID;

/**
 * Reporte 8: listado de choferes de la sucursal, marcando licencias vencidas o por vencer en 30 días.
 * El ADMIN_SUCURSAL ve siempre su sucursal; el ADMIN_SISTEMA indica {@code idSucursal}.
 */
public interface ReporteChoferesService {

    /** Nombre de la plantilla y del archivo PDF. */
    String NOMBRE = "choferes";

    ReporteTabla<FilaChofer> generar(UsuarioAutenticado usuario, UUID idSucursal, FiltroFechas intervalo);
}
