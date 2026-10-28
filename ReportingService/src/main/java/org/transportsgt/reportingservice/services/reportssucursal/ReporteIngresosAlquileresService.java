package org.transportsgt.reportingservice.services.reportssucursal;

import org.transportsgt.reportingservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportssucursal.FilaIngresoAlquiler;

import java.util.UUID;

/**
 * Reporte 10: ingresos por alquiler de buses.
 * El ADMIN_SUCURSAL ve siempre su sucursal; el ADMIN_SISTEMA indica {@code idSucursal}.
 */
public interface ReporteIngresosAlquileresService {

    /** Nombre de la plantilla y del archivo PDF. */
    String NOMBRE = "ingresos-alquileres";

    ReporteTabla<FilaIngresoAlquiler> generar(UsuarioAutenticado usuario, UUID idSucursal, FiltroFechas intervalo);
}
