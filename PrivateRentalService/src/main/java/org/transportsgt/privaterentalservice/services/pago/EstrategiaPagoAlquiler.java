package org.transportsgt.privaterentalservice.services.pago;

import org.transportsgt.eventos.payload.Canal;
import org.transportsgt.privaterentalservice.config.security.UsuarioAutenticado;
import org.transportsgt.privaterentalservice.dto.PagoRealizado;
import org.transportsgt.privaterentalservice.models.Alquiler;

import java.time.LocalDate;

/**
 * Cómo se cobra un alquiler según su canal (en línea con Cartera, mostrador en efectivo).
 */
public interface EstrategiaPagoAlquiler {

    Canal canal();

    /**
     * Mensaje cuando se intenta pagar un alquiler de este canal por el endpoint del otro.
     */
    String indicacion();

    void autorizar(Alquiler alquiler, UsuarioAutenticado usuario);

    PagoRealizado cobrar(Alquiler alquiler, LocalDate fechaPago, String authorizationHeader);
}
