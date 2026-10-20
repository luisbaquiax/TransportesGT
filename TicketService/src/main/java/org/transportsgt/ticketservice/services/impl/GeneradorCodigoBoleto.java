package org.transportsgt.ticketservice.services.impl;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Código legible del boleto: {@code BOL-} + 8 caracteres sin letras ni dígitos que se confunden (O/0, I/1, L).
 * Con 31^8 combinaciones un choque es improbable; el índice único {@code boleto_codigo_key} queda de respaldo.
 */
@Component
class GeneradorCodigoBoleto {

    static final String PREFIJO = "BOL-";
    private static final char[] ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int LARGO = 8;

    private final SecureRandom aleatorio = new SecureRandom();

    String generar() {
        StringBuilder codigo = new StringBuilder(PREFIJO);
        for (int i = 0; i < LARGO; i++) {
            codigo.append(ALFABETO[aleatorio.nextInt(ALFABETO.length)]);
        }
        return codigo.toString();
    }
}
