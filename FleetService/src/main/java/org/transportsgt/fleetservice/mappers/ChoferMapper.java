package org.transportsgt.fleetservice.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.transportsgt.eventos.payload.DatosChofer;
import org.transportsgt.fleetservice.dto.request.ActualizarChoferRequest;
import org.transportsgt.fleetservice.dto.request.CrearChoferRequest;
import org.transportsgt.fleetservice.dto.response.ChoferResponse;
import org.transportsgt.fleetservice.models.Chofer;

@Mapper(componentModel = "spring")
public interface ChoferMapper {

    ChoferResponse toResponse(Chofer chofer);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fotoUrl", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "creadoEn", ignore = true)
    @Mapping(target = "actualizadoEn", ignore = true)
    Chofer toEntity(CrearChoferRequest request);

    /** Actualiza todos los campos editables excepto activo y foto. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "idSucursal", ignore = true)
    @Mapping(target = "fotoUrl", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "creadoEn", ignore = true)
    @Mapping(target = "actualizadoEn", ignore = true)
    void actualizar(@MappingTarget Chofer chofer, ActualizarChoferRequest request);

    /**
     * Payload de ChoferRegistrado / ChoferActualizado (de ahí salen los avisos de licencia).
     */
    @Mapping(target = "idChofer", source = "id")
    DatosChofer toDatos(Chofer chofer);
}
