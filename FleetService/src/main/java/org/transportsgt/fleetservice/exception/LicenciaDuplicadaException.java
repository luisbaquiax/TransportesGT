package org.transportsgt.fleetservice.exception;

public class LicenciaDuplicadaException extends ConflictoException {

    public LicenciaDuplicadaException(String numeroLicencia) {
        super("LICENCIA_DUPLICADA", "Ya existe un chofer con la licencia " + numeroLicencia);
    }
}
