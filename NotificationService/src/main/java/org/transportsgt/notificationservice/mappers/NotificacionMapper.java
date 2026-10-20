package org.transportsgt.notificationservice.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.transportsgt.notificationservice.dto.response.NotificacionDetalleResponse;
import org.transportsgt.notificationservice.dto.response.NotificacionResumenResponse;
import org.transportsgt.notificationservice.models.Notificacion;

@Mapper(componentModel = "spring")
public interface NotificacionMapper {

    @Mapping(target = "idNotificacion", source = "id")
    NotificacionResumenResponse toResumen(Notificacion notificacion);

    @Mapping(target = "idNotificacion", source = "id")
    NotificacionDetalleResponse toDetalle(Notificacion notificacion);
}
