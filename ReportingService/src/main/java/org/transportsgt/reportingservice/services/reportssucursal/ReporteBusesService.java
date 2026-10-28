package org.transportsgt.reportingservice.services.reportssucursal;

import org.transportsgt.reportingservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportssucursal.FilaBus;
import org.transportsgt.eventos.payload.flota.EstadoOperativo;

import java.util.UUID;

/**
 * Reporte 7: listado de buses de la sucursal, con filtro opcional por estado operativo.
 * El ADMIN_SUCURSAL ve siempre su sucursal; el ADMIN_SISTEMA indica {@code idSucursal}.
 */
public interface ReporteBusesService {

    /** Nombre de la plantilla y del archivo PDF. */
    String NOMBRE = "buses";

    ReporteTabla<FilaBus> generar(UsuarioAutenticado usuario, UUID idSucursal, FiltroFechas intervalo, EstadoOperativo estado);
}
