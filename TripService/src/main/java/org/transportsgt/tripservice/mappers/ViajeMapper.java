package org.transportsgt.tripservice.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.transportsgt.tripservice.dto.response.Responses;
import org.transportsgt.tripservice.models.BusRef;
import org.transportsgt.tripservice.models.ChoferRef;
import org.transportsgt.tripservice.models.RegistroLlegada;
import org.transportsgt.tripservice.models.RegistroSalida;
import org.transportsgt.tripservice.models.Viaje;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring")
public interface ViajeMapper {

    @Mapping(target = "id", source = "viaje.id")
    @Mapping(target = "idRuta", source = "viaje.ruta.id")
    @Mapping(target = "version", source = "viaje.version")
    Responses.ViajeResponse toResponse(Viaje viaje, Responses.BusResumen bus, Responses.ChoferResumen chofer,
                                       Responses.RegistroSalidaResponse registroSalida,
                                       Responses.RegistroLlegadaResponse registroLlegada);

    @Mapping(target = "id", source = "viaje.id")
    @Mapping(target = "idRuta", source = "viaje.ruta.id")
    Responses.ViajeResumenResponse toResumen(Viaje viaje, String placaBus);

    @Mapping(target = "idBus", source = "id")
    @Mapping(target = "capacidad", source = "capacidadPasajeros")
    Responses.BusResumen toBusResumen(BusRef bus);

    @Mapping(target = "idChofer", source = "id")
    @Mapping(target = "fechaVencimientoLicencia", source = "fechaVencimientoLicencia", qualifiedByName = "inicioDelDiaUtc")
    Responses.ChoferResumen toChoferResumen(ChoferRef chofer);

    Responses.RegistroSalidaResponse toResponse(RegistroSalida salida);

    Responses.RegistroLlegadaResponse toResponse(RegistroLlegada llegada);

    @Named("inicioDelDiaUtc")
    default OffsetDateTime inicioDelDiaUtc(LocalDate fecha) {
        return fecha == null ? null : fecha.atStartOfDay().atOffset(ZoneOffset.UTC);
    }
}
