package org.transportsgt.notificationservice.dto.response;

import org.transportsgt.notificationservice.models.EstadoNotificacion;
import org.transportsgt.notificationservice.models.TipoNotificacion;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificacionResumenResponse(
        UUID idNotificacion,
        TipoNotificacion tipo,
        String correoDestino,
        UUID idUsuario,
        String asunto,
        EstadoNotificacion estado,
        short intentos,
        String ultimoError,
        OffsetDateTime proximoIntentoEn,
        OffsetDateTime creadoEn,
        OffsetDateTime enviadaEn
) {
}
