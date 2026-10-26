package org.transportsgt.operationalcostsservice.config.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.payload.Rol;

import java.util.UUID;

/**
 * Convierte los claims del JWT en {@link UsuarioAutenticado}
 */
@Component
public class ConvertidorUsuarioAutenticado {

    public UsuarioAutenticado desde(Jwt jwt) {
        String idSucursal = jwt.getClaimAsString("idSucursal");
        return new UsuarioAutenticado(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString("correo"),
                Rol.valueOf(jwt.getClaimAsString("rol")),
                idSucursal == null ? null : UUID.fromString(idSucursal),
                jwt.getClaimAsString("nombre"));
    }
}
