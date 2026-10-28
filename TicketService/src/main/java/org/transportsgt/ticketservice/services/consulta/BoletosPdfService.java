package org.transportsgt.ticketservice.services.consulta;

import org.transportsgt.ticketservice.config.security.authentication.UsuarioAutenticado;

import java.util.UUID;

public interface BoletosPdfService {

    /**
     * PDF con un QR por boleto confirmado. Lo ven los mismos que el detalle de la compra (si no, 404); la compra debe
     * estar {@code CONFIRMADA} (si no, 409 {@code COMPRA_NO_CONFIRMADA}).
     */
    byte[] generar(UsuarioAutenticado usuario, UUID idCompra);
}
