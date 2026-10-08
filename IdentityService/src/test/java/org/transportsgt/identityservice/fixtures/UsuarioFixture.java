package org.transportsgt.identityservice.fixtures;

import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.identityservice.models.Sucursal;
import org.transportsgt.identityservice.models.Usuario;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class UsuarioFixture {

    public static final String HASH = "$2a$10$hashDePrueba";

    private UsuarioFixture() {
    }

    public static Usuario de(UUID id, Rol rol, Sucursal sucursal) {
        return Usuario.builder()
                .id(id)
                .correo(rol.name().toLowerCase() + "." + id.toString().substring(0, 8) + "@transportes.gt")
                .nombreCompleto("Usuario " + rol.name())
                .hashContrasena(HASH)
                .rol(rol)
                .sucursal(sucursal)
                .activo(true)
                .version(0L)
                .contrasenaCambiadaEn(OffsetDateTime.parse("2026-10-01T08:00:00Z"))
                .build();
    }

    public static Usuario adminSistema(UUID id) {
        return de(id, Rol.ADMIN_SISTEMA, null);
    }

    public static Usuario adminSucursal(UUID id, Sucursal sucursal) {
        return de(id, Rol.ADMIN_SUCURSAL, sucursal);
    }

    public static Usuario cajero(UUID id, Sucursal sucursal) {
        return de(id, Rol.CAJERO, sucursal);
    }

    public static Usuario cliente(UUID id) {
        return de(id, Rol.CLIENTE, null);
    }
}
