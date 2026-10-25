package org.transportsgt.ticketservice.services.abordaje.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.payload.EstadoViaje;
import org.transportsgt.ticketservice.config.security.UsuarioAutenticado;
import org.transportsgt.ticketservice.exception.BoletoNoValidoException;
import org.transportsgt.ticketservice.exception.SinPermisoSucursalException;
import org.transportsgt.ticketservice.exception.ViajeNoDisponibleException;
import org.transportsgt.ticketservice.exception.ViajeNoEncontradoException;
import org.transportsgt.ticketservice.models.Boleto;
import org.transportsgt.ticketservice.models.EstadoBoleto;
import org.transportsgt.ticketservice.models.ViajeRef;
import org.transportsgt.ticketservice.repositories.ViajeRefRepository;

import java.util.UUID;

/**
 * Reglas del abordaje: lo registra el personal de la sucursal de origen, antes de la salida.
 */
@Component
@RequiredArgsConstructor
class ValidadorAbordaje {

    private final ViajeRefRepository viajeRefRepository;

    /**
     * El viaje existe, no se eliminó y sale de la sucursal del usuario.
     */
    ViajeRef viajeDeSuSucursal(UsuarioAutenticado usuario, UUID idViaje) {
        ViajeRef viaje = viajeRefRepository.findById(idViaje)
                .filter(encontrado -> !encontrado.getEliminado())
                .orElseThrow(ViajeNoEncontradoException::new);
        if (!usuario.perteneceASucursal(viaje.getIdSucursalOrigen())) {
            throw new SinPermisoSucursalException();
        }
        return viaje;
    }

    /**
     * Se aborda mientras el viaje está {@code PROGRAMADO}: al registrar la salida pasa a {@code EN_TRANSITO}.
     */
    void abordable(ViajeRef viaje) {
        if (viaje.getEstado() != EstadoViaje.PROGRAMADO) {
            throw new ViajeNoDisponibleException("El viaje está " + viaje.getEstado() + ": ya no admite abordajes");
        }
    }

    void boletoDelViaje(Boleto boleto, ViajeRef viaje) {
        if (!boleto.getIdViaje().equals(viaje.getId())) {
            throw new BoletoNoValidoException("El boleto " + boleto.getCodigo() + " es de otro viaje");
        }
        if (boleto.getEstado() != EstadoBoleto.CONFIRMADO) {
            throw new BoletoNoValidoException("El boleto " + boleto.getCodigo() + " está " + boleto.getEstado());
        }
    }
}
