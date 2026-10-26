package org.transportsgt.tripservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import org.transportsgt.eventos.payload.EstadoViaje;
import org.transportsgt.eventos.payload.TipoViaje;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class Requests {

    private Requests() {
    }

    /**
     * Filtros opcionales de GET /viajes (todos combinados con AND). {@code desde} y {@code hasta} son inclusivos;
     * {@code sinAsignar=true} lista solo los alquileres PENDIENTE_ASIGNACION.
     */
    public record FiltroViajes(
            TipoViaje tipoViaje,
            EstadoViaje estado,
            UUID idRuta,
            UUID idSucursal,
            OffsetDateTime desde,
            OffsetDateTime hasta,
            Boolean sinAsignar
    ) {
    }

    public record RutaRequest(
            @NotNull(message = "idSucursalOrigen es obligatorio") UUID idSucursalOrigen,
            @NotNull(message = "idSucursalDestino es obligatorio") UUID idSucursalDestino,
            @NotNull(message = "distanciaKm es obligatoria")
            @DecimalMin(value = "0.01", message = "distanciaKm debe ser mayor que cero") BigDecimal distanciaKm,
            @NotNull(message = "precioBoleto es obligatorio")
            @DecimalMin(value = "0", message = "precioBoleto no puede ser negativo") BigDecimal precioBoleto
    ) {
    }

    public record ViajeRegularRequest(
            @NotNull(message = "idRuta es obligatoria") UUID idRuta,
            @NotNull(message = "idBus es obligatorio") UUID idBus,
            @NotNull(message = "idChofer es obligatorio") UUID idChofer,
            @NotNull(message = "fechaHoraSalida es obligatoria")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fechaHoraSalida,
            @NotNull(message = "fechaHoraLlegadaEstimada es obligatoria")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fechaHoraLlegadaEstimada
    ) {
    }

    public record ViajeUpdateRequest(
            UUID idBus,
            UUID idChofer,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fechaHoraSalida,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fechaHoraLlegadaEstimada
    ) {
    }

    public record AsignacionRequest(
            @NotNull(message = "idBus es obligatorio") UUID idBus,
            @NotNull(message = "idChofer es obligatorio") UUID idChofer
    ) {
    }

    public record SalidaRequest(
            @NotNull(message = "fechaHoraSalidaReal es obligatoria")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fechaHoraSalidaReal,
            @NotNull(message = "kilometrajeInicial es obligatorio")
            @DecimalMin(value = "0", message = "kilometrajeInicial no puede ser negativo") BigDecimal kilometrajeInicial
    ) {
    }

    public record LlegadaRequest(
            @NotNull(message = "fechaHoraLlegadaReal es obligatoria")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fechaHoraLlegadaReal,
            @NotNull(message = "kilometrajeFinal es obligatorio")
            @DecimalMin(value = "0", message = "kilometrajeFinal no puede ser negativo") BigDecimal kilometrajeFinal,
            @NotNull(message = "gastoCombustibleTotal es obligatorio")
            @DecimalMin(value = "0", message = "gastoCombustibleTotal no puede ser negativo")
            BigDecimal gastoCombustibleTotal
    ) {
    }
}
