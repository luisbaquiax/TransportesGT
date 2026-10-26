package org.transportsgt.customerwalletservice.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Perfil del usuario autenticado con el saldo de su cartera.
 */
public record ClienteResponse(
        UUID idUsuario,
        String nombreCompleto,
        String correo,
        String nit,
        String dpi,
        String telefono,
        String direccion,
        Boolean activo,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal saldo
) {
}
