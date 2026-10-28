package org.transportsgt.identityservice.services.usuario;

import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.identityservice.models.Sucursal;
import org.transportsgt.identityservice.models.Usuario;

import java.util.UUID;

/** Alta de personal; la usan POST /usuarios y POST /sucursales (primer administrador). */
public interface AltaPersonalService {

    /**
     * Alta de personal (ADMIN_SISTEMA, ADMIN_SUCURSAL, CAJERO, CHOFER) con contraseña aleatoria y enlace
     * de ACTIVACION. Valida correo, sucursal según el rol y que el creador esté activo (y sea ADMIN_SISTEMA
     * si el nuevo es ADMIN_SUCURSAL). Publica UsuarioCreado y EnlaceContrasenaEmitido.
     * Debe llamarse dentro de una transacción.
     */
    Usuario registrarPersonal(String nombreCompleto, String correo, Rol rol, Sucursal sucursal,
                              UUID idCreador, String ip);
}
