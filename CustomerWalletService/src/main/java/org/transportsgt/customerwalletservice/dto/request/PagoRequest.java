package org.transportsgt.customerwalletservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.transportsgt.eventos.payload.TipoReferencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cobro de una compra de boletos o de un alquiler. No lleva idUsuario: el cliente sale del JWT reenviado.
 *
 * @param idReferencia idCompra o idAlquiler; debe ser igual a la cabecera {@code Clave-Idempotencia}
 * @param fechaPago    fecha de negocio elegida por el usuario
 */
public record PagoRequest(
        @Schema(example = "BOLETOS") @NotNull TipoReferencia tipoReferencia,
        @Schema(example = "6f1c2a7e-3b4d-4e5f-8a9b-0c1d2e3f4a5b") @NotNull UUID idReferencia,
        @Schema(example = "170.00", type = "string") @NotNull @DecimalMin(value = "0.01")
        @Digits(integer = 10, fraction = 2) BigDecimal monto,
        @Schema(example = "2026-10-20") @NotNull LocalDate fechaPago,
        @Schema(example = "2 boletos Xela → Capital") @Size(max = 250) String descripcion
) {
}
