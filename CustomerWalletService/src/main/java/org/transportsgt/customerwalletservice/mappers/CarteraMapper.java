package org.transportsgt.customerwalletservice.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.transportsgt.customerwalletservice.dto.response.CarteraResponse;
import org.transportsgt.customerwalletservice.dto.response.MovimientoResponse;
import org.transportsgt.customerwalletservice.dto.response.PagoConsultaResponse;
import org.transportsgt.customerwalletservice.dto.response.PagoResponse;
import org.transportsgt.customerwalletservice.models.Cartera;
import org.transportsgt.customerwalletservice.models.MovimientoCartera;

@Mapper(componentModel = "spring")
public interface CarteraMapper {

    @Mapping(target = "idCartera", source = "id")
    CarteraResponse toResponse(Cartera cartera);

    @Mapping(target = "idMovimiento", source = "id")
    MovimientoResponse toResponse(MovimientoCartera movimiento);

    @Mapping(target = "idPago", source = "id")
    PagoResponse toPago(MovimientoCartera pago);

    @Mapping(target = "idPago", source = "id")
    @Mapping(target = "fechaPago", source = "fechaMovimiento")
    PagoConsultaResponse toConsulta(MovimientoCartera pago);
}
