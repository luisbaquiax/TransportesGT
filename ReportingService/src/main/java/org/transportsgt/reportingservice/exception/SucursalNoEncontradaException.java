package org.transportsgt.reportingservice.exception;

import java.util.UUID;

/**
 * La sucursal aún no está en {@code rep_sucursal}.
 */
public class SucursalNoEncontradaException extends RecursoNoEncontradoException {

    public SucursalNoEncontradaException(UUID idSucursal) {
        super("NO_ENCONTRADO", "No existe la sucursal " + idSucursal);
    }
}
