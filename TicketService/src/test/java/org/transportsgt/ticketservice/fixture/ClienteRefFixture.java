package org.transportsgt.ticketservice.fixture;

import org.springframework.security.oauth2.jwt.Jwt;
import org.transportsgt.ticketservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.ticketservice.models.ClienteRef;
import org.transportsgt.eventos.payload.identidad.Rol;

import java.time.Instant;
import java.util.UUID;

public final class ClienteRefFixture {

    public static final String NIT = "1234567K";
    public static final String NOMBRE = "Carlos Méndez";

    private ClienteRefFixture() {
    }

    public static ClienteRef cliente(UUID idUsuario) {
        return ClienteRef.builder().idUsuario(idUsuario).nit(NIT).nombreCompleto(NOMBRE).activo(true).version(0L).build();
    }

    public static ClienteRef sinNit(UUID idUsuario) {
        ClienteRef cliente = cliente(idUsuario);
        cliente.setNit(null);
        return cliente;
    }

    public static ClienteRef inactivo(UUID idUsuario) {
        ClienteRef cliente = cliente(idUsuario);
        cliente.setActivo(false);
        return cliente;
    }

    public static UsuarioAutenticado usuario(UUID id, Rol rol, UUID idSucursal) {
        return new UsuarioAutenticado(id, "usuario@transportes.gt", rol, idSucursal, NOMBRE);
    }

    public static UsuarioAutenticado comprador(UUID id) {
        return usuario(id, Rol.CLIENTE, null);
    }

    /** JWT ya validado, como lo entrega Spring Security al controlador. */
    public static Jwt jwt(UUID id, Rol rol, UUID idSucursal) {
        Jwt.Builder jwt = Jwt.withTokenValue("token-de-prueba")
                .header("alg", "HS256")
                .subject(id.toString())
                .claim("rol", rol.name())
                .claim("correo", "usuario@transportes.gt")
                .claim("nombre", NOMBRE)
                .issuedAt(Instant.parse("2026-10-20T10:00:00Z"))
                .expiresAt(Instant.parse("2026-10-20T11:00:00Z"));
        if (idSucursal != null) {
            jwt.claim("idSucursal", idSucursal.toString());
        }
        return jwt.build();
    }
}
