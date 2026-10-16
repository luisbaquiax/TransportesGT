package org.transportsgt.customerwalletservice.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.transportsgt.customerwalletservice.dto.response.ClienteBusquedaResponse;
import org.transportsgt.customerwalletservice.dto.response.ClienteResponse;
import org.transportsgt.customerwalletservice.models.Cliente;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface ClienteMapper {

    @Mapping(target = "saldo", source = "saldo")
    ClienteResponse toResponse(Cliente cliente, BigDecimal saldo);

    ClienteBusquedaResponse toBusqueda(Cliente cliente);
}
