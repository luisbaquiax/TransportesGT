package org.transportsgt.privaterentalservice.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.transportsgt.eventos.payload.alquileres.DatosAlquilerCancelado;
import org.transportsgt.eventos.payload.alquileres.DatosAlquilerConfirmado;
import org.transportsgt.eventos.payload.alquileres.DatosAlquilerCotizado;
import org.transportsgt.eventos.payload.alquileres.DatosAlquilerSolicitado;
import org.transportsgt.privaterentalservice.dto.response.AlquilerResponse;
import org.transportsgt.privaterentalservice.dto.response.CotizacionAlquilerResponse;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.models.CotizacionAlquiler;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AlquilerMapper {

    @Mapping(target = "cotizaciones", ignore = true)
    AlquilerResponse toResponse(Alquiler alquiler);

    /**
     * Detalle: el alquiler con su historial de cotizaciones.
     */
    @Mapping(target = "cotizaciones", source = "cotizaciones")
    AlquilerResponse toDetalle(Alquiler alquiler, List<CotizacionAlquiler> cotizaciones);

    List<AlquilerResponse> toResponseList(List<Alquiler> alquileres);

    CotizacionAlquilerResponse toResponse(CotizacionAlquiler cotizacion);

    List<CotizacionAlquilerResponse> toCotizacionList(List<CotizacionAlquiler> cotizaciones);

    @Mapping(target = "idAlquiler", source = "a.id")
    @Mapping(target = "idUsuarioCliente", source = "a.idUsuarioCliente")
    @Mapping(target = "nombreCliente", source = "a.nombreCliente")
    @Mapping(target = "nit", source = "a.nitCliente")
    @Mapping(target = "idSucursalOrigen", source = "a.idSucursalOrigen")
    @Mapping(target = "origen", source = "a.origen")
    @Mapping(target = "destino", source = "a.destino")
    @Mapping(target = "fechaHoraSalida", source = "a.fechaHoraSalida")
    @Mapping(target = "fechaHoraRetorno", source = "a.fechaHoraRetorno")
    @Mapping(target = "numeroPasajeros", expression = "java(a.getNumeroPasajeros().intValue())")
    @Mapping(target = "precioEstimado", source = "a.precioEstimado")
    DatosAlquilerSolicitado toDatosSolicitado(Alquiler a);

    @Mapping(target = "idAlquiler", source = "alquiler.id")
    @Mapping(target = "idUsuarioCliente", source = "alquiler.idUsuarioCliente")
    @Mapping(target = "precioFinal", source = "alquiler.precioFinal")
    @Mapping(target = "fechaHoraLlegadaEstimada", source = "alquiler.fechaHoraLlegadaEstimada")
    DatosAlquilerCotizado toDatosCotizado(Alquiler alquiler, java.util.UUID idUsuarioCotizador);

    @Mapping(target = "idAlquiler", source = "a.id")
    @Mapping(target = "idUsuarioCliente", source = "a.idUsuarioCliente")
    @Mapping(target = "nombreCliente", source = "a.nombreCliente")
    @Mapping(target = "nit", source = "a.nitCliente")
    @Mapping(target = "idSucursalOrigen", source = "a.idSucursalOrigen")
    @Mapping(target = "origen", source = "a.origen")
    @Mapping(target = "destino", source = "a.destino")
    @Mapping(target = "precioTotal", source = "a.precioFinal")
    @Mapping(target = "idPago", source = "a.idPago")
    DatosAlquilerConfirmado toDatosConfirmado(Alquiler a);

    @Mapping(target = "idAlquiler", source = "a.id")
    @Mapping(target = "idUsuarioCliente", source = "a.idUsuarioCliente")
    @Mapping(target = "nombreCliente", source = "a.nombreCliente")
    @Mapping(target = "nit", source = "a.nitCliente")
    @Mapping(target = "idSucursalOrigen", source = "a.idSucursalOrigen")
    @Mapping(target = "precioTotal", source = "a.precioFinal")
    @Mapping(target = "idPago", source = "a.idPago")
    @Mapping(target = "canceladoEn", expression = "java(a.getCanceladoEn() == null ? null : a.getCanceladoEn().toInstant())")
    DatosAlquilerCancelado toDatosCancelado(Alquiler a, String motivo);
}
