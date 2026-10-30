package org.transportsgt.customerwalletservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Perfil comercial. El nombre y el correo no se editan aquí: vienen de Identidad.
 * El NIT debe contener exactamente 9 dígitos.
 */
public record ActualizarPerfilRequest(
        @Schema(example = "123456789", description = "Exactamente 9 dígitos, sin espacios ni guiones.")
        @Pattern(regexp = "^[0-9]{9}$", message = "debe tener 9 dígitos")
        String nit,
        @Schema(example = "2456789010101")
        @Pattern(regexp = "^[0-9]{13}$", message = "debe tener 13 dígitos")
        String dpi,
        @Schema(example = "55551234")
        @Pattern(regexp = "^[0-9]{8}$", message = "debe tener 8 dígitos")
        String telefono,
        @Schema(example = "4a. Calle 12-35, Zona 1, Quetzaltenango")
        @Size(max = 250)
        String direccion
) {
}
