package org.transportsgt.notificationservice.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.transportsgt.notificationservice.dto.response.RecordatorioLicenciaResponse;
import org.transportsgt.notificationservice.models.RecordatorioLicencia;

@Mapper(componentModel = "spring")
public interface RecordatorioMapper {

    @Mapping(target = "idRecordatorio", source = "id")
    @Mapping(target = "idChofer", source = "chofer.idChofer")
    @Mapping(target = "nombreChofer", source = "chofer.nombreCompleto")
    @Mapping(target = "idSucursal", source = "chofer.idSucursal")
    RecordatorioLicenciaResponse toResponse(RecordatorioLicencia recordatorio);
}
