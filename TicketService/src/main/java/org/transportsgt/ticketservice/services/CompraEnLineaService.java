package org.transportsgt.ticketservice.services;

import org.transportsgt.ticketservice.config.security.UsuarioAutenticado;
import org.transportsgt.ticketservice.dto.request.CompraRequest;
import org.transportsgt.ticketservice.dto.response.ResultadoCompra;

/**
 * Saga de la compra en línea (plan §4.1): validar → reservar (Tx1) → cobrar en Cartera → confirmar (Tx2), liberar los
 * asientos o dejar la compra pendiente.
 */
public interface CompraEnLineaService {

    /**
     * @param jwtComprador token del comprador, reenviado a Cartera (no se escribe en los logs)
     */
    ResultadoCompra comprar(UsuarioAutenticado comprador, String jwtComprador, CompraRequest request);
}
