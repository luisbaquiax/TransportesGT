package org.transportsgt.ticketservice.services.abordaje;

import org.transportsgt.ticketservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.ticketservice.dto.response.AbordajeResponse;
import org.transportsgt.ticketservice.dto.response.Resultado;

import java.util.UUID;

public interface AbordajeService {

    /**
     * Registra que el pasajero del boleto {@code codigo} abordó el viaje. Solo SUC o CAJ de la sucursal de origen,
     * con el viaje {@code PROGRAMADO} y el boleto {@code CONFIRMADO} de ese viaje. Si ya había abordado devuelve el
     * registro original ({@link Resultado#existente}).
     */
    Resultado<AbordajeResponse> registrar(UsuarioAutenticado usuario, UUID idViaje, String codigo);
}
