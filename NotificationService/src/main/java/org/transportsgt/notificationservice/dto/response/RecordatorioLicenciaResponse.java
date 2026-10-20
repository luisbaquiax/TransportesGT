package org.transportsgt.notificationservice.dto.response;

import org.transportsgt.notificationservice.models.EstadoRecordatorio;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Aviso de vencimiento de licencia programado.
 */
public record RecordatorioLicenciaResponse(
        UUID idRecordatorio,
        UUID idChofer,
        String nombreChofer,
        UUID idSucursal,
        LocalDate fechaVencimiento,
        short diasAnticipacion,
        LocalDate fechaEnvioProgramada,
        EstadoRecordatorio estado,
        OffsetDateTime enviadoEn
) {
}
