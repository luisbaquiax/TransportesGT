package org.transportsgt.customerwalletservice.exception;

/**
 * El perfil no tiene NIT: no puede comprar ni alquilar en línea.
 */
public class PerfilIncompletoException extends ReglaNegocioException {

    public PerfilIncompletoException() {
        super("PERFIL_INCOMPLETO", "Complete su NIT en el perfil para comprar o alquilar en línea");
    }
}
