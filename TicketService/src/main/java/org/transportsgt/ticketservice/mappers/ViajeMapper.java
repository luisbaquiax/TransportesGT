package org.transportsgt.ticketservice.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.transportsgt.ticketservice.dto.response.ViajeDisponibleResponse;
import org.transportsgt.ticketservice.models.ViajeRef;

@Mapper(componentModel = "spring")
public interface ViajeMapper {

    @Mapping(target = "idViaje", source = "viaje.id")
    @Mapping(target = "capacidad", source = "viaje.capacidadBus")
    @Mapping(target = "asientosDisponibles", source = "asientosDisponibles")
    ViajeDisponibleResponse toDisponible(ViajeRef viaje, int asientosDisponibles);
}
