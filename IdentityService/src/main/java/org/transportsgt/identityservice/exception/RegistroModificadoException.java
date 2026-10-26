package org.transportsgt.identityservice.exception;

/**
 * La versión enviada por el cliente ya no es la actual (otro usuario guardó antes).
 */
public class RegistroModificadoException extends ConflictoException {

    public RegistroModificadoException() {
        super("CONFLICTO_CONCURRENCIA", "El registro fue modificado por otro usuario, recárguelo");
    }
}
