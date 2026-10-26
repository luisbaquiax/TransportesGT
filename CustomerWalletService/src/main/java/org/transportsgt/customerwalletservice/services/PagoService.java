package org.transportsgt.customerwalletservice.services;

import org.transportsgt.customerwalletservice.config.security.UsuarioAutenticado;
import org.transportsgt.customerwalletservice.dto.request.PagoRequest;
import org.transportsgt.customerwalletservice.dto.response.PagoConsultaResponse;
import org.transportsgt.customerwalletservice.dto.response.PagoResponse;
import org.transportsgt.customerwalletservice.dto.response.Resultado;
import org.transportsgt.eventos.payload.TipoReferencia;

import java.util.UUID;

/**
 * Cobros de la saga de Boletos y Alquileres (contrato del catálogo §5).
 */
public interface PagoService {

    /**
     * Cobra una compra o un alquiler. La clave debe ser igual a {@code idReferencia}.
     * Un PAGO ya existente con la misma cartera y monto se devuelve ({@code creado = false}); distinto, 409.
     * No publica evento.
     */
    Resultado<PagoResponse> pagar(UsuarioAutenticado usuario, String claveIdempotencia, PagoRequest request);

    /**
     * Consulta el PAGO de una referencia (recuperación de la saga).
     *
     * @param idUsuario dueño de la cartera (JWT), o {@code null} con la clave interna (cualquier pago)
     */
    PagoConsultaResponse consultarPago(UUID idUsuario, TipoReferencia tipoReferencia, UUID idReferencia);
}
