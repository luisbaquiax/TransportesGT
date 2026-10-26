package org.transportsgt.reportingservice.dto.response.reportsadmin;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Reporte 6: una ruta con las coordenadas de su origen y destino (Leaflet).
 */
public record FilaMapaRuta(
        UUID idRuta,
        String origen,
        BigDecimal latitudOrigen,
        BigDecimal longitudOrigen,
        String destino,
        BigDecimal latitudDestino,
        BigDecimal longitudDestino,
        BigDecimal distanciaKm,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal precioBoleto
) {
}
