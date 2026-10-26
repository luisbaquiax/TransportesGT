package org.transportsgt.customerwalletservice.fixture;

import org.springframework.security.oauth2.jwt.Jwt;
import org.transportsgt.customerwalletservice.config.security.UsuarioAutenticado;
import org.transportsgt.customerwalletservice.models.Cliente;
import org.transportsgt.eventos.payload.Rol;

import java.time.Instant;
import java.util.UUID;

public final class ClienteFixture {

    public static final String NOMBRE = "Carlos Méndez";
    public static final String CORREO = "carlos.mendez@gmail.com";
    public static final String NIT = "1234567K";
    public static final String DPI = "2456789010101";

    private ClienteFixture() {
    }

    /** Cliente activo, con NIT, ya sincronizado con la versión 0 del usuario. */
    public static Cliente cliente(UUID idUsuario) {
        return Cliente.builder()
                .id(UUID.randomUUID())
                .idUsuario(idUsuario)
                .nombreCompleto(NOMBRE)
                .correo(CORREO)
                .nit(NIT)
                .activo(true)
                .versionUsuario(0L)
                .version(0L)
                .build();
    }

    public static Cliente sinNit(UUID idUsuario) {
        Cliente cliente = cliente(idUsuario);
        cliente.setNit(null);
        return cliente;
    }

    public static Cliente inactivo(UUID idUsuario) {
        Cliente cliente = cliente(idUsuario);
        cliente.setActivo(false);
        return cliente;
    }

    public static UsuarioAutenticado usuario(UUID id, Rol rol) {
        return new UsuarioAutenticado(id, CORREO, rol, null, NOMBRE);
    }

    public static UsuarioAutenticado usuario(UUID id) {
        return usuario(id, Rol.CLIENTE);
    }

    /** JWT ya validado, como lo entrega Spring Security al controlador. */
    public static Jwt jwt(UUID id, Rol rol) {
        return Jwt.withTokenValue("token-de-prueba")
                .header("alg", "HS256")
                .subject(id.toString())
                .claim("rol", rol.name())
                .claim("correo", CORREO)
                .claim("nombre", NOMBRE)
                .issuedAt(Instant.parse("2026-10-20T10:00:00Z"))
                .expiresAt(Instant.parse("2026-10-20T11:00:00Z"))
                .build();
    }
}
