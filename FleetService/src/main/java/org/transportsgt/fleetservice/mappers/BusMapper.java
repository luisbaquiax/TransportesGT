package org.transportsgt.fleetservice.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.transportsgt.eventos.payload.flota.DatosBus;
import org.transportsgt.fleetservice.dto.request.ActualizarBusRequest;
import org.transportsgt.fleetservice.dto.request.CrearBusRequest;
import org.transportsgt.fleetservice.dto.response.BusResponse;
import org.transportsgt.fleetservice.models.Bus;

@Mapper(componentModel = "spring")
public interface BusMapper {

    /** fotoUrl nunca expone la referencia interna: es la ruta privada GET /buses/{id}/foto. */
    @Mapping(target = "fotoUrl", expression = "java(rutaFoto(bus))")
    BusResponse toResponse(Bus bus);

    /**
     * Ruta relativa al context-path del servicio (/v1/fleet). {@code v} cambia con cada versión del registro y
     * con cada foto nueva, para que el navegador no use una copia vieja de su caché.
     */
    default String rutaFoto(Bus bus) {
        if (bus.getFotoUrl() == null || bus.getFotoUrl().isBlank()) {
            return null;
        }
        return "/buses/" + bus.getId() + "/foto?v=" + (bus.getVersion() == null ? 0 : bus.getVersion());
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "estadoOperativo", ignore = true)
    @Mapping(target = "fotoUrl", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "creadoEn", ignore = true)
    @Mapping(target = "actualizadoEn", ignore = true)
    Bus toEntity(CrearBusRequest request);

    /** Actualiza solo los campos editables: ni estado, ni kilometraje, ni activo. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "idSucursal", ignore = true)
    @Mapping(target = "estadoOperativo", ignore = true)
    @Mapping(target = "kilometrajeActual", ignore = true)
    @Mapping(target = "fotoUrl", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "creadoEn", ignore = true)
    @Mapping(target = "actualizadoEn", ignore = true)
    void actualizar(@MappingTarget Bus bus, ActualizarBusRequest request);

    /**
     * Payload de BusRegistrado / BusActualizado (el kilometraje y la foto no viajan en el evento).
     */
    @Mapping(target = "idBus", source = "id")
    DatosBus toDatos(Bus bus);
}
