package org.transportsgt.fleetservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.transportsgt.eventos.payload.flota.TipoLicencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Datos editables de un chofer. Al guardar se publica ChoferActualizado
 * (Notificaciones reprograma los avisos de licencia).
 */
public record ActualizarChoferRequest(
        @Schema(example = "Juan Carlos Pérez")
        @NotBlank @Size(max = 150) String nombreCompleto,

        @Schema(example = "chofer.xela@transportes.gt")
        @NotBlank @Email @Size(max = 150) String correo,

        @Schema(example = "5555-1234")
        @NotBlank @Size(max = 20) @Pattern(regexp = "^[0-9+\\- ]*$",
                message = "solo puede contener dígitos, espacios, + y -") String telefono,

        @Schema(example = "LIC-XEL-001")
        @NotBlank @Size(max = 30) String numeroLicencia,

        @Schema(example = "B")
        @NotNull TipoLicencia tipoLicencia,

        @Schema(example = "2027-03-15")
        @NotNull LocalDate fechaVencimientoLicencia,

        @Schema(example = "150.00")
        @NotNull @DecimalMin(value = "0") @Digits(integer = 8, fraction = 2) BigDecimal salarioBaseViaje,

        @Schema(nullable = true, description = "Usuario de Identidad con rol CHOFER")
        UUID idUsuario,

        @Schema(example = "1", description = "Versión actual; si ya no coincide, responde 409", nullable = true)
        Long version
) {
}
