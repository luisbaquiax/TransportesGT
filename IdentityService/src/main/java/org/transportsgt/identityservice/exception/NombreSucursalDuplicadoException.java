package org.transportsgt.identityservice.exception;

public class NombreSucursalDuplicadoException extends ConflictoException {

    public NombreSucursalDuplicadoException(String nombre) {
        super("NOMBRE_SUCURSAL_DUPLICADO", "Ya existe una sucursal con el nombre " + nombre);
    }
}
