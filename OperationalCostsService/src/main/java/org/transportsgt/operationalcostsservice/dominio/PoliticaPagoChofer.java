package org.transportsgt.operationalcostsservice.dominio;

import org.transportsgt.eventos.payload.TipoViaje;

import java.math.BigDecimal;

/**
 * Única fuente de verdad del multiplicador de salario del chofer según el tipo de viaje.
 * La tabla {@code pago_chofer} solo admite 1.00 y 1.15 (CHECK en {@code V1__esquema_inicial.sql});
 * este enum concentra la regla que antes estaba como literales en el manejador de eventos.
 */
public enum PoliticaPagoChofer {

    REGULAR(new BigDecimal("1.00")),
    ALQUILER_PRIVADO(new BigDecimal("1.15"));

    private final BigDecimal multiplicador;

    PoliticaPagoChofer(BigDecimal multiplicador) {
        this.multiplicador = multiplicador;
    }

    public BigDecimal multiplicador() {
        return multiplicador;
    }

    /** Cualquier tipo no listado explícitamente se rige por la tarifa regular (comportamiento previo). */
    public static PoliticaPagoChofer para(TipoViaje tipoViaje) {
        return tipoViaje == TipoViaje.ALQUILER_PRIVADO ? ALQUILER_PRIVADO : REGULAR;
    }
}
