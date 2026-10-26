package org.transportsgt.privaterentalservice.services.comun;

import org.springframework.stereotype.Component;
import org.transportsgt.eventos.Nit;
import org.transportsgt.privaterentalservice.exception.SolicitudInvalidaException;

/**
 * Envuelve {@link Nit} de la librería común para inyectarlo (misma normalización que Boletos y Cartera).
 */
@Component
public class NormalizadorNit {

    /**
     * NIT de perfil o de evento: normalizado, o null si viene vacío.
     */
    public String normalizar(String nit) {
        return Nit.normalizar(nit);
    }

    /**
     * Mostrador: vacío o CF es consumidor final; si no, debe tener formato válido.
     */
    public String paraMostrador(String nit) {
        String normalizado = Nit.normalizar(nit);
        if (normalizado == null || Nit.esConsumidorFinal(normalizado)) {
            return Nit.CONSUMIDOR_FINAL;
        }
        if (!Nit.tieneFormatoValido(normalizado)) {
            throw new SolicitudInvalidaException("NIT_INVALIDO",
                    "El NIT debe tener de 2 a 20 letras o dígitos (o CF para consumidor final)");
        }
        return normalizado;
    }

    public boolean esConsumidorFinal(String nit) {
        return Nit.esConsumidorFinal(nit);
    }
}
