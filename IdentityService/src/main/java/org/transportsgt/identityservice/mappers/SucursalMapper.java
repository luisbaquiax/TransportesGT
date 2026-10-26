package org.transportsgt.identityservice.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.transportsgt.eventos.payload.DatosSucursal;
import org.transportsgt.identityservice.dto.request.SucursalRequest;
import org.transportsgt.identityservice.dto.response.SucursalResponse;
import org.transportsgt.identityservice.models.Sucursal;

@Mapper(componentModel = "spring")
public interface SucursalMapper {

    SucursalResponse toResponse(Sucursal sucursal);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "activa", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "creadoEn", ignore = true)
    @Mapping(target = "actualizadoEn", ignore = true)
    Sucursal toEntity(SucursalRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "activa", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "creadoEn", ignore = true)
    @Mapping(target = "actualizadoEn", ignore = true)
    void actualizar(@MappingTarget Sucursal sucursal, SucursalRequest request);

    /**
     * Payload de SucursalCreada / SucursalActualizada.
     */
    @Mapping(target = "idSucursal", source = "id")
    DatosSucursal toDatos(Sucursal sucursal);
}
