package org.transportsgt.fleetservice.dto.response;

import org.transportsgt.eventos.payload.flota.EstadoOperativo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BusResponse(
        UUID id,
        UUID idSucursal,
        String placa,
        String marca,
        String modelo,
        Short anioFabricacion,
        Short capacidadPasajeros,
        EstadoOperativo estadoOperativo,
        BigDecimal kilometrajeActual,
        String fotoUrl,
        boolean activo,
        Long version,
        OffsetDateTime creadoEn,
        OffsetDateTime actualizadoEn
) {
}
