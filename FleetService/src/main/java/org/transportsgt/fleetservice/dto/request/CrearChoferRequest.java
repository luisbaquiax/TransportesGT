package org.transportsgt.fleetservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.transportsgt.eventos.payload.TipoLicencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Datos para registrar un chofer. {@code idUsuario} enlaza con el usuario de Identidad (rol CHOFER)
 * para que pueda registrar su salida; no se valida contra Identidad.
 */
public record CrearChoferRequest(
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
        @NotNull @FutureOrPresent(message = "la fecha de vencimiento no puede estar en el pasado")
        LocalDate fechaVencimientoLicencia,

        @Schema(example = "150.00")
        @NotNull @DecimalMin(value = "0") @Digits(integer = 8, fraction = 2) BigDecimal salarioBaseViaje,

        @Schema(example = "1c9e4a52-0d2b-4f3e-8a77-6f1b2c3d4e5f", nullable = true,
                description = "Usuario de Identidad con rol CHOFER, si tendrá acceso al sistema")
        UUID idUsuario
) {
}
