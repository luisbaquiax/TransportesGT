package org.transportsgt.reportingservice.services.reportssucursal;

import org.transportsgt.reportingservice.config.security.UsuarioAutenticado;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportssucursal.FilaIngresoBoleto;

import java.util.UUID;

/**
 * Reporte 9: ingresos por venta de boletos por viaje, con filtros opcionales por ruta y bus.
 * El ADMIN_SUCURSAL ve siempre su sucursal; el ADMIN_SISTEMA indica {@code idSucursal}.
 */
public interface ReporteIngresosBoletosService {

    /** Nombre de la plantilla y del archivo PDF. */
    String NOMBRE = "ingresos-boletos";

    ReporteTabla<FilaIngresoBoleto> generar(UsuarioAutenticado usuario, UUID idSucursal, FiltroFechas intervalo, UUID idRuta, UUID idBus);
}
