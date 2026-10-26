package org.transportsgt.notificationservice.dto.response;

import org.transportsgt.notificationservice.models.EstadoNotificacion;
import org.transportsgt.notificationservice.models.TipoNotificacion;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificacionDetalleResponse(
        UUID idNotificacion,
        TipoNotificacion tipo,
        String correoDestino,
        UUID idUsuario,
        String asunto,
        String cuerpo,
        EstadoNotificacion estado,
        short intentos,
        String ultimoError,
        OffsetDateTime proximoIntentoEn,
        String claveDedup,
        UUID idEventoOrigen,
        OffsetDateTime creadoEn,
        OffsetDateTime enviadaEn
) {
}
