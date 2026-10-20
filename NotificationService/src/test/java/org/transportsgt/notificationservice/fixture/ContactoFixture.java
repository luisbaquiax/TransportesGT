package org.transportsgt.notificationservice.fixture;

import org.springframework.security.oauth2.jwt.Jwt;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.notificationservice.config.security.UsuarioAutenticado;
import org.transportsgt.notificationservice.models.Contacto;
import org.transportsgt.notificationservice.services.bandeja.Destinatario;

import java.time.Instant;
import java.util.UUID;

public final class ContactoFixture {

    public static final UUID SUCURSAL_XELA = UUID.fromString("10000000-0000-4000-8000-000000000001");
    public static final UUID SUCURSAL_CAPITAL = UUID.fromString("10000000-0000-4000-8000-000000000002");
    public static final String NOMBRE = "Carlos Méndez";

    private ContactoFixture() {
    }

    public static Contacto cliente(UUID idUsuario) {
        return Contacto.builder().idUsuario(idUsuario).correo("cliente." + idUsuario.toString().substring(0, 8) + "@gmail.com")
                .nombreCompleto(NOMBRE).rol(Rol.CLIENTE).activo(true).version(0L).build();
    }

    public static Contacto adminSucursal(UUID idUsuario, UUID idSucursal) {
        Contacto contacto = cliente(idUsuario);
        contacto.setRol(Rol.ADMIN_SUCURSAL);
        contacto.setIdSucursal(idSucursal);
        contacto.setNombreCompleto("Admin " + idUsuario.toString().substring(0, 4));
        return contacto;
    }

    public static Destinatario destinatario(UUID idUsuario) {
        Contacto contacto = cliente(idUsuario);
        return new Destinatario(idUsuario, contacto.getCorreo(), contacto.getNombreCompleto());
    }

    public static UsuarioAutenticado usuario(Rol rol, UUID idSucursal) {
        return new UsuarioAutenticado(UUID.randomUUID(), "usuario@transportes.gt", rol, idSucursal, NOMBRE);
    }

    /** JWT ya validado, como lo entrega Spring Security al controlador. */
    public static Jwt jwt(Rol rol, UUID idSucursal) {
        Jwt.Builder jwt = Jwt.withTokenValue("token-de-prueba")
                .header("alg", "HS256")
                .subject(UUID.randomUUID().toString())
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
