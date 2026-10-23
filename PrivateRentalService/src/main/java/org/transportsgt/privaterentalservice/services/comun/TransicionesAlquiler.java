package org.transportsgt.privaterentalservice.services.comun;

import org.springframework.stereotype.Component;
import org.transportsgt.privaterentalservice.exception.TransicionInvalidaException;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Ciclo de vida del alquiler
 */
@Component
public class TransicionesAlquiler {

    private final Map<EstadoAlquiler, Set<EstadoAlquiler>> permitidas = new EnumMap<>(EstadoAlquiler.class);

    public TransicionesAlquiler() {
        permitidas.put(EstadoAlquiler.SOLICITADO, EnumSet.of(EstadoAlquiler.COTIZADO, EstadoAlquiler.CANCELADO));
        permitidas.put(EstadoAlquiler.COTIZADO,
                EnumSet.of(EstadoAlquiler.COTIZADO, EstadoAlquiler.CONFIRMADO, EstadoAlquiler.CANCELADO));
        permitidas.put(EstadoAlquiler.CONFIRMADO, EnumSet.of(EstadoAlquiler.FINALIZADO, EstadoAlquiler.CANCELADO));
        permitidas.put(EstadoAlquiler.FINALIZADO, EnumSet.noneOf(EstadoAlquiler.class));
        permitidas.put(EstadoAlquiler.CANCELADO, EnumSet.noneOf(EstadoAlquiler.class));
    }

    public boolean puede(EstadoAlquiler desde, EstadoAlquiler hacia) {
        return permitidas.get(desde).contains(hacia);
    }

    /**
     * @throws TransicionInvalidaException 409 si el estado actual no permite pasar a {@code hacia}
     */
    public void verificar(Alquiler alquiler, EstadoAlquiler hacia, String operacion) {
        if (!puede(alquiler.getEstado(), hacia)) {
            throw new TransicionInvalidaException(alquiler.getEstado(), operacion);
        }
    }

    public void aplicar(Alquiler alquiler, EstadoAlquiler hacia, String operacion) {
        verificar(alquiler, hacia, operacion);
        alquiler.setEstado(hacia);
    }
}
