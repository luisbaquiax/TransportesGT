package org.transportsgt.ticketservice.services.consulta.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.ticketservice.config.security.UsuarioAutenticado;
import org.transportsgt.ticketservice.models.ClienteRef;
import org.transportsgt.ticketservice.models.Compra;
import org.transportsgt.ticketservice.repositories.ClienteRefRepository;
import org.transportsgt.eventos.Nit;
import org.transportsgt.eventos.payload.Rol;

import java.util.Optional;

/**
 * Quién puede ver una compra y con qué NIT se arma el historial de un usuario.
 */
@Component
@RequiredArgsConstructor
class ReglasVisibilidad {

    private final ClienteRefRepository clienteRefRepository;

    /**
     * NIT del perfil del usuario, si tiene uno real (nunca {@code CF}, que es de todas las ventas sin NIT).
     */
    Optional<String> nitDe(UsuarioAutenticado usuario) {
        return clienteRefRepository.findById(usuario.id())
                .map(ClienteRef::getNit)
                .filter(nit -> nit != null && !nit.isBlank() && !Nit.esConsumidorFinal(nit));
    }

    boolean puedeVer(UsuarioAutenticado usuario, Compra compra) {
        if (usuario.esAdminSistema()) {
            return true;
        }
        if (usuario.rol() == Rol.ADMIN_SUCURSAL || usuario.rol() == Rol.CAJERO) {
            if (usuario.perteneceASucursal(compra.getIdSucursalIngreso())) {
                return true;
            }
        }
        if (usuario.id().equals(compra.getIdUsuarioComprador())) {
            return true;
        }
        return nitDe(usuario).map(nit -> nit.equals(compra.getNitComprador())).orElse(false);
    }
}
