package org.transportsgt.operationalcostsservice.config.security.authorization;

import org.transportsgt.operationalcostsservice.config.security.authentication.UsuarioAutenticado;

import org.springframework.stereotype.Component;
import org.transportsgt.operationalcostsservice.exception.OperacionNoAutorizadaException;

import java.util.UUID;

/**
 * Reglas de acceso por rol/sucursal en un solo lugar (SRP): los servicios delegan aquí
 * en lugar de repetir comparaciones ad-hoc de {@code esAdminSucursal()}/{@code idSucursal}.
 */
@Component
public class PoliticaAccesoSucursal {

    /**
     * Solo ADMIN_SISTEMA puede ejecutar la operación.
     */
    public void exigirAdminSistema(UsuarioAutenticado usuario, String mensaje) {
        if (!usuario.esAdminSistema()) {
            throw new OperacionNoAutorizadaException(mensaje);
        }
    }

    /**
     * Un ADMIN_SUCURSAL solo accede a recursos de su propia sucursal; ADMIN_SISTEMA accede a todo.
     * Un recurso sin sucursal (o inexistente, {@code idSucursalRecurso == null}) se deniega a SUC.
     */
    public void exigirMismaSucursal(UsuarioAutenticado usuario, UUID idSucursalRecurso, String mensaje) {
        if (usuario.esAdminSucursal() && !usuario.perteneceASucursal(idSucursalRecurso)) {
            throw new OperacionNoAutorizadaException(mensaje);
        }
    }
}
