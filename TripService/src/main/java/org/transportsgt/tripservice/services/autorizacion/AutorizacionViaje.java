package org.transportsgt.tripservice.services.autorizacion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.tripservice.config.security.UsuarioAutenticado;
import org.transportsgt.tripservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.tripservice.models.Ruta;
import org.transportsgt.tripservice.models.Viaje;
import org.transportsgt.tripservice.repositories.ChoferRefRepository;

import java.util.UUID;

/**
 * Quién puede ver y operar un viaje (plan §3.3): SIS ve todo; SUC y CAJ ven los de su sucursal; CHO solo los
 * suyos. Solo el SUC de la sucursal modifica; la salida y la llegada las registra el SUC o el chofer del viaje.
 * En el listado, las mismas reglas de lectura se aplican en la consulta ({@code ViajeSpecs}).
 */
@Component
@RequiredArgsConstructor
public class AutorizacionViaje {

    private final ChoferRefRepository choferRefRepository;

    public void verificarLectura(Viaje viaje, UsuarioAutenticado usuario) {
        if (usuario.esAdminSistema()) {
            return;
        }
        if (esPersonalDeSucursal(usuario)) {
            if (!usuario.perteneceASucursal(viaje.getIdSucursalOrigen())) {
                throw new OperacionNoAutorizadaException("El viaje no pertenece a su sucursal");
            }
            return;
        }
        if (!esSuViaje(viaje, usuario)) {
            throw new OperacionNoAutorizadaException("Solo puede ver sus propios viajes");
        }
    }

    public void verificarEscritura(Viaje viaje, UsuarioAutenticado usuario) {
        if (!usuario.esAdminSucursal() || !usuario.perteneceASucursal(viaje.getIdSucursalOrigen())) {
            throw new OperacionNoAutorizadaException("Solo el administrador de la sucursal del viaje puede modificarlo");
        }
    }

    /**
     * Programar en una ruta: solo el administrador de su sucursal de origen.
     */
    public void verificarProgramacion(Ruta ruta, UsuarioAutenticado usuario) {
        if (!usuario.perteneceASucursal(ruta.getIdSucursalOrigen())) {
            throw new OperacionNoAutorizadaException(
                    "Solo el administrador de la sucursal de origen puede programar viajes en la ruta");
        }
    }

    public void verificarSalidaLlegada(Viaje viaje, UsuarioAutenticado usuario) {
        if (usuario.esAdminSucursal()) {
            if (!usuario.perteneceASucursal(viaje.getIdSucursalOrigen())) {
                throw new OperacionNoAutorizadaException("El viaje no pertenece a su sucursal");
            }
            return;
        }
        if (!esSuViaje(viaje, usuario)) {
            throw new OperacionNoAutorizadaException("Solo puede registrar salida o llegada de sus propios viajes");
        }
    }

    /**
     * SUC y CAJ: personal de la sucursal (el cajero solo consulta).
     */
    private boolean esPersonalDeSucursal(UsuarioAutenticado usuario) {
        return usuario.esAdminSucursal() || usuario.rol() == Rol.CAJERO;
    }

    /**
     * El chofer asignado es el del usuario (compara con chofer_ref.id_usuario).
     */
    private boolean esSuViaje(Viaje viaje, UsuarioAutenticado usuario) {
        UUID idChofer = viaje.getIdChofer();
        return idChofer != null && choferRefRepository.findById(idChofer)
                .map(chofer -> usuario.id().equals(chofer.getIdUsuario()))
                .orElse(false);
    }
}
