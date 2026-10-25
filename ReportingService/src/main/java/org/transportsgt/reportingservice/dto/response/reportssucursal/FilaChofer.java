package org.transportsgt.reportingservice.dto.response.reportssucursal;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Reporte 8: un chofer de la sucursal.
 *
 * @param estadoLicencia {@code VIGENTE}, {@code POR_VENCER} (30 días o menos), {@code VENCIDA} o {@code SIN_FECHA}
 */
public record FilaChofer(
        UUID idChofer,
        String numeroLicencia,
        String nombre,
        String tipoLicencia,
        LocalDate fechaVencimiento,
        String estadoLicencia,
        boolean activo,
        long viajesRealizados
) {
}
