package org.transportsgt.privaterentalservice.services.pago.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.payload.boletos.Canal;
import org.transportsgt.privaterentalservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.services.autorizacion.AutorizacionAlquiler;
import org.transportsgt.privaterentalservice.services.pago.EstrategiaPagoAlquiler;
import org.transportsgt.privaterentalservice.dto.PagoRealizado;

import java.time.LocalDate;

/** Efectivo en mostrador: sin Cartera, idPago queda null (ck_alquiler_confirmado lo permite en MOSTRADOR). */
@Component
@RequiredArgsConstructor
class PagoEfectivoMostrador implements EstrategiaPagoAlquiler {

    private final AutorizacionAlquiler autorizacion;

    @Override
    public Canal canal() {
        return Canal.MOSTRADOR;
    }

    @Override
    public String indicacion() {
        return "Un alquiler de mostrador se paga con pago-mostrador";
    }

    @Override
    public void autorizar(Alquiler alquiler, UsuarioAutenticado usuario) {
        autorizacion.verificarPersonalMostrador(usuario,
                "Solo administradores de sucursal y cajeros cobran en mostrador");
        autorizacion.verificarGestion(alquiler, usuario);
    }

    @Override
    public PagoRealizado cobrar(Alquiler alquiler, LocalDate fechaPago, String authorizationHeader) {
        return new PagoRealizado(null, fechaPago);
    }
}
