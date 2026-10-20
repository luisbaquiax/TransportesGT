package org.transportsgt.ticketservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Cartera rechazó el cobro. Se propagan su status y su {@code codigo} (422 SALDO_INSUFICIENTE o PERFIL_INCOMPLETO,
 * 403 CUENTA_INACTIVA...).
 */
public class PagoRechazadoException extends BoletosServiceException {

    public PagoRechazadoException(HttpStatus status, String codigo, String mensaje) {
        super(status, codigo, mensaje);
    }
}
