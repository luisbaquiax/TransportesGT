package org.transportsgt.ticketservice.services.compra;

import org.transportsgt.ticketservice.config.security.UsuarioAutenticado;
import org.transportsgt.ticketservice.dto.request.VentaMostradorRequest;
import org.transportsgt.ticketservice.dto.response.CompraResponse;
import org.transportsgt.ticketservice.dto.response.Resultado;

/**
 * Venta presencial (cajero o administrador de sucursal). No usa la cartera.
 */
public interface VentaMostradorService {

    /**
     * Reserva y confirma en una sola transacción y publica VentaBoletosRegistrada. El viaje debe ser de la sucursal
     * del vendedor.
     */
    Resultado<CompraResponse> vender(UsuarioAutenticado vendedor, VentaMostradorRequest request);
}
