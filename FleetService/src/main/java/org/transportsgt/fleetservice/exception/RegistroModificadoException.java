package org.transportsgt.fleetservice.exception;

/**
 * 409: el registro fue modificado por otro usuario (versión desactualizada).
 */
public class RegistroModificadoException extends ConflictoException {

    public RegistroModificadoException() {
        super("CONFLICTO_CONCURRENCIA", "El registro fue modificado por otro usuario, recárguelo");
    }
}
