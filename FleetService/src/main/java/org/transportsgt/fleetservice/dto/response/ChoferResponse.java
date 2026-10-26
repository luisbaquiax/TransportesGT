package org.transportsgt.fleetservice.dto.response;

import org.transportsgt.eventos.payload.TipoLicencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ChoferResponse(
        UUID id,
        UUID idUsuario,
        UUID idSucursal,
        String nombreCompleto,
        String correo,
        String telefono,
        String numeroLicencia,
        TipoLicencia tipoLicencia,
        LocalDate fechaVencimientoLicencia,
        BigDecimal salarioBaseViaje,
        String fotoUrl,
        boolean activo,
        Long version,
        OffsetDateTime creadoEn,
        OffsetDateTime actualizadoEn
) {
}
