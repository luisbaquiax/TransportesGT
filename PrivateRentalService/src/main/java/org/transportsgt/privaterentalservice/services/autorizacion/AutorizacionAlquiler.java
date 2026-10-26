package org.transportsgt.privaterentalservice.services.autorizacion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.privaterentalservice.config.security.UsuarioAutenticado;
import org.transportsgt.privaterentalservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.models.ClienteRef;
import org.transportsgt.privaterentalservice.repositories.ClienteRefRepository;
import org.transportsgt.privaterentalservice.services.comun.NormalizadorNit;

import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AutorizacionAlquiler {

    private final ClienteRefRepository clienteRepository;
    private final NormalizadorNit normalizadorNit;

    public boolean esPersonalMostrador(UsuarioAutenticado usuario) {
        return usuario.esAdminSucursal() || usuario.rol() == Rol.CAJERO;
    }

    public void verificarPersonalMostrador(UsuarioAutenticado usuario, String mensaje) {
        if (!esPersonalMostrador(usuario)) {
            throw new OperacionNoAutorizadaException(mensaje);
        }
    }

    /**
     * SIS gestiona todos; SUC y CAJ, solo los de su sucursal.
     */
    public void verificarGestion(Alquiler alquiler, UsuarioAutenticado usuario) {
        if (!usuario.esAdminSistema() && !usuario.perteneceASucursal(alquiler.getIdSucursalOrigen())) {
            throw new OperacionNoAutorizadaException("El alquiler pertenece a otra sucursal");
        }
    }

    public void verificarDueno(Alquiler alquiler, UsuarioAutenticado usuario) {
        if (!usuario.id().equals(alquiler.getIdUsuarioCliente())) {
            throw new OperacionNoAutorizadaException("Solo el cliente dueño del alquiler puede pagarlo en línea");
        }
    }

    /**
     * Lo ven SIS, el personal de su sucursal, el cliente que lo pidió en línea y el cliente cuyo NIT de perfil coincide
     * con el de un alquiler de mostrador (el mismo criterio que GET /mios).
     */
    public void verificarLectura(Alquiler alquiler, UsuarioAutenticado usuario) {
        if (usuario.esAdminSistema()
                || usuario.id().equals(alquiler.getIdUsuarioCliente())
                || usuario.perteneceASucursal(alquiler.getIdSucursalOrigen())
                || coincideNit(alquiler, usuario.id())) {
            return;
        }
        throw new OperacionNoAutorizadaException("Este alquiler no le pertenece");
    }

    /**
     * NIT del perfil del cliente (normalizado), o null si no tiene perfil o NIT.
     */
    public String nitDelCliente(UUID idUsuario) {
        return clienteRepository.findById(idUsuario)
                .map(ClienteRef::getNit)
                .map(normalizadorNit::normalizar)
                .orElse(null);
    }

    private boolean coincideNit(Alquiler alquiler, UUID idUsuario) {
        if (normalizadorNit.esConsumidorFinal(alquiler.getNitCliente())) {
            return false;
        }
        String nit = nitDelCliente(idUsuario);
        return nit != null && Objects.equals(nit, alquiler.getNitCliente());
    }
}
