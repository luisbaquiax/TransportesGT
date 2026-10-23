package org.transportsgt.tripservice.mappers;

import org.mapstruct.Mapper;
import org.transportsgt.tripservice.dto.response.Responses;
import org.transportsgt.tripservice.models.Ruta;

@Mapper(componentModel = "spring")
public interface RutaMapper {

    Responses.RutaResponse toResponse(Ruta ruta);
}
