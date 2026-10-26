package org.transportsgt.fleetservice.exception;

/**
 * 404: el bus o chofer existe pero no tiene foto (o el archivo ya no está en el almacenamiento).
 */
public class FotoNoEncontradaException extends RecursoNoEncontradoException {
    public FotoNoEncontradaException() {
        super("FOTO_NO_ENCONTRADA", "El registro no tiene foto");
    }
}
