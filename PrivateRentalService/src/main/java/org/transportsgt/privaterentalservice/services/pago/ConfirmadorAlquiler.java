package org.transportsgt.privaterentalservice.services.pago;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.transportsgt.privaterentalservice.dto.PagoRealizado;
import org.transportsgt.privaterentalservice.eventos.EventosAlquiler;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;
import org.transportsgt.privaterentalservice.services.comun.GuardadoAlquiler;
import org.transportsgt.privaterentalservice.services.comun.TransicionesAlquiler;

/**
 * Último paso del pago, compartido por los endpoints y el job de recuperación de la saga.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfirmadorAlquiler {

    private final TransicionesAlquiler transiciones;
    private final GuardadoAlquiler guardado;
    private final EventosAlquiler eventos;

    public Alquiler confirmar(Alquiler a, PagoRealizado pago) {
        transiciones.aplicar(a, EstadoAlquiler.CONFIRMADO, "pagar (requiere COTIZADO)");
        a.setIdPago(pago.idPago());
        a.setFechaPago(pago.fechaPago());
        Alquiler guardado = this.guardado.guardar(a);
        eventos.alquilerConfirmado(guardado);
        //log.info("Alquiler {} confirmado ({}, pago {})", guardado.getId(), guardado.getCanal(), pago.idPago());
        return guardado;
    }
}
