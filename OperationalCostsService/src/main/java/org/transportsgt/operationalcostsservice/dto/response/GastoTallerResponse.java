package org.transportsgt.operationalcostsservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record GastoTallerResponse(
        UUID id,
        UUID idBus,
        UUID idSucursal,
        LocalDate fechaServicio,
        BigDecimal total,
        String observaciones,
        UUID registradoPor,
        OffsetDateTime creadoEn,
        List<LineaTallerResponse> lineas
) {

    public record LineaTallerResponse(
            UUID id,
            UUID idCategoria,
            String nombreCategoria,
            String descripcion,
            String tipo,
            BigDecimal monto
    ) {
    }
}
