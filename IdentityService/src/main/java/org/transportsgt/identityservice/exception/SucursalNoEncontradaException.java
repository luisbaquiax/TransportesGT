package org.transportsgt.identityservice.exception;

import java.util.UUID;

public class SucursalNoEncontradaException extends RecursoNoEncontradoException {

    public SucursalNoEncontradaException(UUID id) {
        super("SUCURSAL_NO_ENCONTRADA", "No se encontró la sucursal " + id);
    }
}
