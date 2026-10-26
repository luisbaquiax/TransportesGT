package org.transportsgt.privaterentalservice.services.comun;

import org.junit.jupiter.api.Test;
import org.transportsgt.privaterentalservice.exception.TransicionInvalidaException;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.transportsgt.privaterentalservice.models.EstadoAlquiler.CANCELADO;
import static org.transportsgt.privaterentalservice.models.EstadoAlquiler.CONFIRMADO;
import static org.transportsgt.privaterentalservice.models.EstadoAlquiler.COTIZADO;
import static org.transportsgt.privaterentalservice.models.EstadoAlquiler.FINALIZADO;
import static org.transportsgt.privaterentalservice.models.EstadoAlquiler.SOLICITADO;

class TransicionesAlquilerTest {

    private final TransicionesAlquiler transiciones = new TransicionesAlquiler();

    /** Debe coincidir con trg_alquiler_transicion (V1) más COTIZADO → COTIZADO (mismo estado: el trigger no valida). */
    @Test
    void elMapaCoincideConElTrigger() {
        Set<String> esperadas = Set.of("SOLICITADO>COTIZADO", "SOLICITADO>CANCELADO",
                "COTIZADO>COTIZADO", "COTIZADO>CONFIRMADO", "COTIZADO>CANCELADO",
                "CONFIRMADO>FINALIZADO", "CONFIRMADO>CANCELADO");
        for (EstadoAlquiler desde : EstadoAlquiler.values()) {
            for (EstadoAlquiler hacia : EstadoAlquiler.values()) {
                assertEquals(esperadas.contains(desde + ">" + hacia), transiciones.puede(desde, hacia),
                        desde + " → " + hacia);
            }
        }
    }

    @Test
    void aplicarCambiaElEstado() {
        Alquiler a = Alquiler.builder().estado(COTIZADO).build();
        transiciones.aplicar(a, CONFIRMADO, "pagar");
        assertEquals(CONFIRMADO, a.getEstado());
    }

    @Test
    void transicionNoPermitidaEs409YNoCambiaElEstado() {
        for (EstadoAlquiler terminal : EnumSet.of(FINALIZADO, CANCELADO)) {
            Alquiler a = Alquiler.builder().estado(terminal).build();
            assertThrows(TransicionInvalidaException.class, () -> transiciones.aplicar(a, SOLICITADO, "x"));
            assertEquals(terminal, a.getEstado());
        }
    }
}
