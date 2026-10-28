package org.transportsgt.identityservice.services.autorizacion;

import org.springframework.stereotype.Component;
import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.identityservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.identityservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.identityservice.models.Usuario;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Quién puede crear, gestionar y ver usuarios. Las reglas gruesas por rol están en SecurityConfig.
 * <ul>
 *   <li>ADMIN_SISTEMA crea ADMIN_SISTEMA y ADMIN_SUCURSAL, y gestiona y ve a todos.</li>
 *   <li>ADMIN_SUCURSAL crea y gestiona CAJERO y CHOFER de su sucursal, y ve a los usuarios de su sucursal.</li>
 *   <li>Cualquier usuario ve su propio registro.</li>
 * </ul>
 */
@Component
public class AutorizacionUsuarios {

    private static final Set<Rol> CREA_ADMIN_SISTEMA = EnumSet.of(Rol.ADMIN_SISTEMA, Rol.ADMIN_SUCURSAL);
    private static final Set<Rol> CREA_ADMIN_SUCURSAL = EnumSet.of(Rol.CAJERO, Rol.CHOFER);

    /**
     * Valida que el autenticado pueda crear un usuario con {@code rol}.
     *
     * @param idSucursalPedida la del cuerpo (puede ser null)
     * @return la sucursal donde se crea: la pedida para SIS (o null) y siempre la propia para SUC
     */
    public UUID sucursalDeAlta(UsuarioAutenticado autenticado, Rol rol, UUID idSucursalPedida) {
        if (autenticado.esAdminSistema() && CREA_ADMIN_SISTEMA.contains(rol)) {
            return idSucursalPedida;
        }
        if (autenticado.esAdminSucursal() && CREA_ADMIN_SUCURSAL.contains(rol)) {
            if (idSucursalPedida != null && !idSucursalPedida.equals(autenticado.idSucursal())) {
                throw new OperacionNoAutorizadaException("Solo puede crear usuarios en su propia sucursal");
            }
            return autenticado.idSucursal();
        }
        throw new OperacionNoAutorizadaException(
                "El rol " + autenticado.rol() + " no puede crear usuarios con rol " + rol);
    }

    /**
     * ADMIN_SISTEMA gestiona a cualquiera; ADMIN_SUCURSAL solo a CAJERO y CHOFER de su sucursal.
     */
    public void verificarGestion(UsuarioAutenticado autenticado, Usuario objetivo) {
        if (autenticado.esAdminSistema()) {
            return;
        }
        boolean permitido = autenticado.esAdminSucursal()
                && CREA_ADMIN_SUCURSAL.contains(objetivo.getRol())
                && Objects.equals(idSucursalDe(objetivo), autenticado.idSucursal());
        if (!permitido) {
            throw new OperacionNoAutorizadaException("No puede modificar este usuario");
        }
    }

    /**
     * SIS ve a todos, SUC a los de su sucursal y cada usuario a sí mismo.
     */
    public void verificarLectura(UsuarioAutenticado autenticado, Usuario objetivo) {
        boolean mismoUsuario = objetivo.getId().equals(autenticado.id());
        boolean mismaSucursal = autenticado.esAdminSucursal()
                && Objects.equals(idSucursalDe(objetivo), autenticado.idSucursal());
        if (!mismoUsuario && !autenticado.esAdminSistema() && !mismaSucursal) {
            throw new OperacionNoAutorizadaException("No puede consultar este usuario");
        }
    }

    /**
     * Filtro de sucursal del listado: SIS el que pida (o ninguno); SUC siempre la suya; los demás no listan.
     */
    public UUID sucursalVisible(UsuarioAutenticado autenticado, UUID idSucursalPedida) {
        if (autenticado.esAdminSucursal()) {
            if (idSucursalPedida != null && !idSucursalPedida.equals(autenticado.idSucursal())) {
                throw new OperacionNoAutorizadaException("Solo puede consultar los usuarios de su sucursal");
            }
            return autenticado.idSucursal();
        }
        if (!autenticado.esAdminSistema()) {
            throw new OperacionNoAutorizadaException("No puede consultar el listado de usuarios");
        }
        return idSucursalPedida;
    }

    private UUID idSucursalDe(Usuario usuario) {
        return usuario.getSucursal() == null ? null : usuario.getSucursal().getId();
    }
}
