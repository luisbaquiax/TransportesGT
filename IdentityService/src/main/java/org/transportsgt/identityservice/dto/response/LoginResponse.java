package org.transportsgt.identityservice.dto.response;

import java.time.Instant;

public record LoginResponse(
        String token,
        Instant expiraEn,
        UsuarioSesionResponse usuario
) {
}
