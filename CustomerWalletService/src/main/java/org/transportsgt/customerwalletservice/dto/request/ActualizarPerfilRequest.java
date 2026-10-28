package org.transportsgt.customerwalletservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Perfil comercial. El nombre y el correo no se editan aquí: vienen de Identidad.
 * El NIT se normaliza (mayúsculas, sin guiones ni espacios) y {@code CF} se rechaza.
 */
public record ActualizarPerfilRequest(
        @Schema(example = "1234567-K", description = "Se guarda normalizado (1234567K). No puede ser CF.")
        @Size(max = 20)
        String nit,
        @Schema(example = "2456789010101")
        @Pattern(regexp = "^[0-9]{13}$", message = "debe tener 13 dígitos")
        String dpi,
        @Schema(example = "55551234")
        @Pattern(regexp = "^[0-9+\\- ]{8,20}$", message = "teléfono inválido")
        String telefono,
        @Schema(example = "4a. Calle 12-35, Zona 1, Quetzaltenango")
        @Size(max = 250)
        String direccion
) {
}
