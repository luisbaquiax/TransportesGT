package org.transportsgt.operationalcostsservice.exception;

import org.springframework.http.HttpStatus;

/** Regla de negocio de dominio (p. ej. LICENCIA_VENCIDA, TARIFA_INEXISTENTE). */
public class ReglaIntegridadException extends CostosException {
    public ReglaIntegridadException(HttpStatus status, String codigo, String mensaje) {
        super(status, codigo, mensaje);
    }
}
