package org.transportsgt.privaterentalservice.services.pago;

import org.transportsgt.privaterentalservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.privaterentalservice.dto.request.PagoAlquilerRequest;
import org.transportsgt.privaterentalservice.dto.response.AlquilerResponse;

import java.util.UUID;

/**
 * Pago de un alquiler COTIZADO: COTIZADO -> CONFIRMADO y AlquilerConfirmado.
 */
public interface PagoAlquilerService {

    /**
     * Cliente dueño de un alquiler en línea; cobra en Cartera reenviando su token.
     */
    AlquilerResponse pagarEnLinea(UUID id, PagoAlquilerRequest request, UsuarioAutenticado usuario,
                                  String authorizationHeader);

    /**
     * SUC o CAJ de la sucursal: efectivo en mostrador, sin Cartera.
     */
    AlquilerResponse pagarMostrador(UUID id, PagoAlquilerRequest request, UsuarioAutenticado usuario);
}
