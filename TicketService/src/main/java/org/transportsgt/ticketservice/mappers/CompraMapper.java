package org.transportsgt.ticketservice.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.transportsgt.ticketservice.dto.response.BoletoResponse;
import org.transportsgt.ticketservice.dto.response.CompraResponse;
import org.transportsgt.ticketservice.models.Boleto;
import org.transportsgt.ticketservice.models.Compra;

@Mapper(componentModel = "spring")
public interface CompraMapper {

    @Mapping(target = "idCompra", source = "id")
    @Mapping(target = "asientos", source = "boletos")
    CompraResponse toResponse(Compra compra);

    BoletoResponse toResponse(Boleto boleto);
}
