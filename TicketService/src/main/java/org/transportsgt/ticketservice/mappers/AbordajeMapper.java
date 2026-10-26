package org.transportsgt.ticketservice.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.transportsgt.ticketservice.dto.response.AbordajeResponse;
import org.transportsgt.ticketservice.dto.response.PasajeroResponse;
import org.transportsgt.ticketservice.models.Boleto;

@Mapper(componentModel = "spring")
public interface AbordajeMapper {

    @Mapping(target = "nombreComprador", source = "compra.nombreComprador")
    AbordajeResponse toResponse(Boleto boleto);

    @Mapping(target = "idCompra", source = "compra.id")
    @Mapping(target = "canal", source = "compra.canal")
    @Mapping(target = "nombreComprador", source = "compra.nombreComprador")
    @Mapping(target = "nitComprador", source = "compra.nitComprador")
    PasajeroResponse toPasajero(Boleto boleto);
}
