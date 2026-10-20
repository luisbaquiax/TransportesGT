package org.transportsgt.notificationservice.services.bitacora;

import org.transportsgt.notificationservice.dto.response.NotificacionDetalleResponse;
import org.transportsgt.notificationservice.dto.response.NotificacionResumenResponse;
import org.transportsgt.notificationservice.dto.response.PaginaResponse;
import org.transportsgt.notificationservice.models.EstadoNotificacion;
import org.transportsgt.notificationservice.models.TipoNotificacion;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Bitácora de correos (administrador de sistema).
 */
public interface BitacoraService {

    /**
     * @param desde día de creación en Guatemala, inclusivo; nulo = sin límite
     * @param hasta día de creación en Guatemala, inclusivo; nulo = sin límite
     */
    PaginaResponse<NotificacionResumenResponse> listar(EstadoNotificacion estado, TipoNotificacion tipo,
                                                       LocalDate desde, LocalDate hasta, int pagina, int tamano);

    NotificacionDetalleResponse detalle(UUID idNotificacion);

    /**
     * Vuelve a encolar un correo FALLIDA: PENDIENTE, 0 intentos y listo para enviar ya.
     *
     * @throws org.transportsgt.notificationservice.exception.ReintentoNoPermitidoException si no está FALLIDA o es un
     *                                                                                      ENLACE_CONTRASENA
     */
    NotificacionResumenResponse reintentar(UUID idNotificacion);
}
