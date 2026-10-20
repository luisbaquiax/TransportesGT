package org.transportsgt.ticketservice.exception;

/**
 * El perfil del comprador ({@code cliente_ref}) no existe o no tiene NIT.
 */
public class PerfilIncompletoException extends ReglaNegocioException {

    public PerfilIncompletoException() {
        super("PERFIL_INCOMPLETO", "Complete su NIT en el perfil para comprar en línea");
    }
}
