package org.transportsgt.fleetservice.exception;

public class PlacaDuplicadaException extends ConflictoException {

    public PlacaDuplicadaException(String placa) {
        super("PLACA_DUPLICADA", "Ya existe un bus con la placa " + placa);
    }
}
