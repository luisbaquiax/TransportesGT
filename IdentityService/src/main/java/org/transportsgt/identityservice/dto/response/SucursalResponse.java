package org.transportsgt.identityservice.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SucursalResponse(
        UUID id,
        String nombre,
        String departamento,
        String direccion,
        String telefono,
        BigDecimal latitud,
        BigDecimal longitud,
        boolean activa,
        Long version,
        OffsetDateTime creadoEn,
        OffsetDateTime actualizadoEn
) {
}
