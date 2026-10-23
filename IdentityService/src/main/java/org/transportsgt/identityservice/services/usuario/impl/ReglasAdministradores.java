package org.transportsgt.identityservice.services.usuario.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.identityservice.exception.UltimoAdministradorSistemaException;
import org.transportsgt.identityservice.exception.UltimoAdministradorSucursalException;
import org.transportsgt.identityservice.models.Usuario;
import org.transportsgt.identityservice.repositories.UsuarioRepository;

/**
 * Mismas reglas que los triggers fn_proteger_ultimo_admin y fn_proteger_ultimo_admin_sucursal (que quedan como
 * respaldo ante solicitudes simultáneas): aquí dan un 409 claro antes de llegar a la base.
 */
@Component
@RequiredArgsConstructor
class ReglasAdministradores {

    private final UsuarioRepository usuarioRepository;

    void verificarNoEsUltimo(Usuario usuario) {
        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            return;
        }
        if (usuario.getRol() == Rol.ADMIN_SISTEMA && usuarioRepository.countByRolAndActivoTrue(Rol.ADMIN_SISTEMA) <= 1) {
            throw new UltimoAdministradorSistemaException();
        }
        if (usuario.getRol() == Rol.ADMIN_SUCURSAL && usuarioRepository.countBySucursalIdAndRolAndActivoTrue(
                usuario.getSucursal() == null ? null : usuario.getSucursal().getId(), Rol.ADMIN_SUCURSAL) <= 1) {
            throw new UltimoAdministradorSucursalException();
        }
    }
}
