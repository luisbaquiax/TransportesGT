package org.transportsgt.identityservice.dto.response;

import org.transportsgt.eventos.payload.PropositoContrasena;

/**
 * @param correoEnmascarado p. ej. {@code a***z@transportes.gt}
 */
public record EnlaceContrasenaResponse(
        PropositoContrasena proposito,
        String correoEnmascarado
) {
}
