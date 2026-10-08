package org.transportsgt.identityservice.dto.response;

public record SucursalCreadaResponse(
        SucursalResponse sucursal,
        UsuarioResponse administrador
) {
}
