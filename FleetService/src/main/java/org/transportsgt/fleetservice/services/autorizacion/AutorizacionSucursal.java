package org.transportsgt.fleetservice.services.autorizacion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.fleetservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.fleetservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.fleetservice.exception.RegistroModificadoException;
import org.transportsgt.fleetservice.exception.SucursalInactivaException;
import org.transportsgt.fleetservice.exception.SucursalNoEncontradaException;
import org.transportsgt.fleetservice.repositories.SucursalRefRepository;

import java.util.UUID;

/**
 * Reglas de autorización y validación compartidas por buses y choferes (SRP: un solo lugar
 * para "quién puede gestionar qué", en vez de duplicarlas en cada servicio).
 *
 * <ul>
 *   <li>ADMIN_SISTEMA consulta todo; la escritura la restringe el {@code SecurityConfig}.</li>
 *   <li>ADMIN_SUCURSAL solo gestiona registros de <em>su</em> sucursal.</li>
 *   <li>Cada registro pertenece a una única sucursal, que debe existir y estar activa al crear.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AutorizacionSucursal {

    private final SucursalRefRepository sucursalRefRepository;

    /**
     * Lanza 403 si el usuario no puede gestionar un registro de la sucursal indicada.
     *
     * @param recurso nombre del recurso para el mensaje ("bus", "chofer")
     */
    public void verificarGestion(UsuarioAutenticado autenticado, UUID idSucursalRecurso, String recurso) {
        if (autenticado.esAdminSistema()) {
            return;
        }
        if (!autenticado.esAdminSucursal() || !autenticado.perteneceASucursal(idSucursalRecurso)) {
            throw new OperacionNoAutorizadaException("No puede gestionar este " + recurso);
        }
    }

    /**
     * La sucursal del token debe existir y estar activa antes de crear un registro en ella.
     */
    public void validarSucursalGestionada(UUID idSucursal) {
        if (idSucursal == null) {
            throw new OperacionNoAutorizadaException("El token no tiene sucursal asignada");
        }
        if (!sucursalRefRepository.existsById(idSucursal)) {
            throw new SucursalNoEncontradaException(idSucursal);
        }
        if (!sucursalRefRepository.existsByIdAndActivaTrue(idSucursal)) {
            throw new SucursalInactivaException(idSucursal.toString());
        }
    }

    /**
     * Control de concurrencia optimista: si el cliente envía una versión, debe coincidir con la actual.
     */
    public void verificarVersion(Long versionEnviada, Long versionActual) {
        if (versionEnviada != null && !versionEnviada.equals(versionActual)) {
            throw new RegistroModificadoException();
        }
    }
}
