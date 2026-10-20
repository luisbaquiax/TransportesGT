package org.transportsgt.ticketservice.exception;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Uno o más asientos ya están {@code RESERVADO} o {@code CONFIRMADO} en el viaje ({@code uq_asiento_activo}).
 */
public class AsientoNoDisponibleException extends ConflictoException {

    public AsientoNoDisponibleException(Collection<? extends Number> asientos) {
        super("ASIENTO_NO_DISPONIBLE", mensaje(asientos));
    }

    /**
     * Otra compra tomó un asiento entre la revisión y la inserción ({@code uq_asiento_activo}).
     */
    public AsientoNoDisponibleException() {
        super("ASIENTO_NO_DISPONIBLE", "Uno de los asientos elegidos acaba de ser tomado por otra compra; elija otro");
    }

    private static String mensaje(Collection<? extends Number> asientos) {
        String lista = asientos.stream().map(String::valueOf).sorted().collect(Collectors.joining(", "));
        return asientos.size() == 1
                ? "El asiento " + lista + " ya está reservado o vendido"
                : "Los asientos " + lista + " ya están reservados o vendidos";
    }
}
