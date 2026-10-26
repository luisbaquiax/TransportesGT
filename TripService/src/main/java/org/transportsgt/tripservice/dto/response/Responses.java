package org.transportsgt.tripservice.dto.response;

import org.transportsgt.eventos.payload.EstadoOperativo;
import org.transportsgt.eventos.payload.EstadoViaje;
import org.transportsgt.eventos.payload.TipoViaje;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class Responses {

    private Responses() {
    }

    public record RutaResponse(
            UUID id,
            UUID idSucursalOrigen,
            UUID idSucursalDestino,
            BigDecimal distanciaKm,
            BigDecimal precioBoleto,
            OffsetDateTime creadoEn,
            OffsetDateTime actualizadoEn
    ) {
    }

    public record BusResumen(UUID idBus, String placa, Integer capacidad, EstadoOperativo estadoOperativo,
                             Boolean activo) {
    }

    public record ChoferResumen(UUID idChofer, String nombreCompleto, OffsetDateTime fechaVencimientoLicencia,
                                BigDecimal salarioBaseViaje, Boolean activo) {
    }

    public record ViajeResponse(
            UUID id,
            TipoViaje tipoViaje,
            EstadoViaje estado,
            UUID idRuta,
            UUID idSucursalOrigen,
            String nombreOrigen,
            String nombreDestino,
            UUID idAlquiler,
            OffsetDateTime fechaHoraSalida,
            OffsetDateTime fechaHoraLlegadaEstimada,
            BigDecimal precioBoleto,
            Short numeroPasajeros,
            BusResumen bus,
            ChoferResumen chofer,
            RegistroSalidaResponse registroSalida,
            RegistroLlegadaResponse registroLlegada,
            Long version
    ) {
    }

    /**
     * Datos públicos de un viaje para el historial de compras. Sin chofer, kilometraje ni registros de salida o
     * llegada: lo consulta cualquier usuario autenticado.
     */
    public record ViajeResumenResponse(
            UUID id,
            TipoViaje tipoViaje,
            EstadoViaje estado,
            UUID idRuta,
            UUID idSucursalOrigen,
            String nombreOrigen,
            String nombreDestino,
            OffsetDateTime fechaHoraSalida,
            OffsetDateTime fechaHoraLlegadaEstimada,
            BigDecimal precioBoleto,
            String placaBus
    ) {
    }

    public record RegistroSalidaResponse(
            UUID id,
            OffsetDateTime fechaHoraSalidaReal,
            BigDecimal kilometrajeInicial,
            UUID registradoPor
    ) {
    }

    public record RegistroLlegadaResponse(
            UUID id,
            OffsetDateTime fechaHoraLlegadaReal,
            BigDecimal kilometrajeFinal,
            BigDecimal distanciaRecorridaKm,
            BigDecimal gastoCombustibleTotal,
            BigDecimal salarioBaseChofer,
            UUID registradoPor
    ) {
    }

    public record RepublicacionResponse(int rutas, int viajes) {
    }
}
