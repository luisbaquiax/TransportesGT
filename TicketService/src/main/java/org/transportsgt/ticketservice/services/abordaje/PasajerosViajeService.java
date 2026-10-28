package org.transportsgt.ticketservice.services.abordaje;

import org.transportsgt.ticketservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.ticketservice.dto.response.PasajerosViajeResponse;

import java.util.UUID;

public interface PasajerosViajeService {

    /**
     * Boletos confirmados del viaje, por asiento, con su estado de abordaje. Solo SUC o CAJ de la sucursal de origen.
     */
    PasajerosViajeResponse pasajeros(UsuarioAutenticado usuario, UUID idViaje);
}
