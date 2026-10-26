package org.transportsgt.privaterentalservice.mappers;

import org.mapstruct.Mapper;
import org.transportsgt.privaterentalservice.dto.response.TarifaResponse;
import org.transportsgt.privaterentalservice.models.TarifaAlquiler;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TarifaMapper {

    TarifaResponse toResponse(TarifaAlquiler tarifa);

    List<TarifaResponse> toResponseList(List<TarifaAlquiler> tarifas);
}
