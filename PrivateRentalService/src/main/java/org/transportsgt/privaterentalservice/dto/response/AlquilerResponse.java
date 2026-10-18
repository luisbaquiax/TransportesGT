package org.transportsgt.privaterentalservice.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import org.transportsgt.eventos.payload.Canal;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AlquilerResponse(
        UUID id,
        Canal canal,
        EstadoAlquiler estado,
        UUID idUsuarioCliente,
        String nombreCliente,
        String nitCliente,
        UUID idSucursalOrigen,
        String origen,
        String destino,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX") OffsetDateTime fechaHoraSalida,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX") OffsetDateTime fechaHoraRetorno,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX") OffsetDateTime fechaHoraLlegadaEstimada,
        Boolean idaYVuelta,
        Short numeroPasajeros,
        BigDecimal latitudOrigen,
        BigDecimal longitudOrigen,
        BigDecimal latitudDestino,
        BigDecimal longitudDestino,
        BigDecimal distanciaEstimadaKm,
        BigDecimal precioEstimado,
        BigDecimal precioFinal,
        UUID idPago,
        LocalDate fechaPago,
        UUID idViaje,
        UUID idBus,
        String placaBus,
        UUID idChofer,
        String nombreChofer,
        String motivoCancelacion,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX") OffsetDateTime canceladoEn,
        Long version,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX") OffsetDateTime creadoEn,
        @Schema(description = "Solo en detalle") List<CotizacionAlquilerResponse> cotizaciones
) {
}
