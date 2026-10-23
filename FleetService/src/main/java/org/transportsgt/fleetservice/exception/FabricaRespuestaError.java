package org.transportsgt.fleetservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Arma el cuerpo {@link RespuestaError} de toda respuesta de error (handler global, 401 y 403). */
@Component
public class FabricaRespuestaError {

    public RespuestaError crear(HttpStatus status, String error, String codigo, String ruta) {
        return new RespuestaError(LocalDateTime.now(), status.value(), error, codigo, ruta);
    }
}
