package org.transportsgt.operationalcostsservice.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CategoriaResponse(
        UUID id,
        String nombre,
        String descripcion,
        Boolean activa,
        OffsetDateTime creadoEn
) {
}
