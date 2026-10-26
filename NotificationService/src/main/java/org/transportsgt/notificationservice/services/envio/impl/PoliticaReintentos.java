package org.transportsgt.notificationservice.services.envio.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.notificationservice.config.NotificacionesProperties;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Espera exponencial entre reintentos (1, 2, 4, 8 min con la espera inicial de 1 min) y límite de intentos.
 */
@Component
@RequiredArgsConstructor
class PoliticaReintentos {

    static final int LARGO_MAXIMO_ERROR = 500;
    private static final Pattern CORREO = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");

    private final NotificacionesProperties propiedades;

    /**
     * @param intentos fallos acumulados, contando el que acaba de ocurrir (1 = primer fallo)
     */
    Duration espera(int intentos) {
        return propiedades.envio().esperaInicial().multipliedBy(1L << Math.max(0, intentos - 1));
    }

    boolean agotado(int intentos) {
        return intentos >= propiedades.envio().maxIntentos();
    }

    /**
     * Para {@code ultimo_error}: sin direcciones de correo y de largo acotado.
     */
    String recortar(String error) {
        if (error == null || error.isBlank()) {
            return "Error desconocido";
        }
        String limpio = CORREO.matcher(error.strip()).replaceAll("[correo]");
        return limpio.length() <= LARGO_MAXIMO_ERROR ? limpio : limpio.substring(0, LARGO_MAXIMO_ERROR - 1) + "…";
    }
}
