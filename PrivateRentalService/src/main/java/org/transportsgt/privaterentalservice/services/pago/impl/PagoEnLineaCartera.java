package org.transportsgt.privaterentalservice.services.pago.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.payload.boletos.Canal;
import org.transportsgt.privaterentalservice.clients.CarteraClient;
import org.transportsgt.privaterentalservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.services.autorizacion.AutorizacionAlquiler;
import org.transportsgt.privaterentalservice.services.pago.EstrategiaPagoAlquiler;
import org.transportsgt.privaterentalservice.dto.PagoRealizado;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Saga de pago: Cartera con Clave-Idempotencia = idAlquiler; si no responde, 503 sin confirmar.
 */
@Component
@RequiredArgsConstructor
class PagoEnLineaCartera implements EstrategiaPagoAlquiler {

    private final CarteraClient carteraClient;
    private final AutorizacionAlquiler autorizacion;

    @Override
    public Canal canal() {
        return Canal.EN_LINEA;
    }

    @Override
    public String indicacion() {
        return "Un alquiler en línea se paga con /pago (cartera)";
    }

    @Override
    public void autorizar(Alquiler alquiler, UsuarioAutenticado usuario) {
        autorizacion.verificarDueno(alquiler, usuario);
    }

    @Override
    public PagoRealizado cobrar(Alquiler a, LocalDate fechaPago, String jwtUsuario) {
        UUID idPago = carteraClient.pagar(jwtUsuario, a.getId(), a.getPrecioFinal(),
                fechaPago, "Alquiler " + a.getOrigen() + " → " + a.getDestino());
        return new PagoRealizado(idPago, fechaPago);
    }
}
