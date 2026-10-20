package org.transportsgt.ticketservice.services.impl;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneradorCodigoBoletoTest {

    private final GeneradorCodigoBoleto generador = new GeneradorCodigoBoleto();

    @Test
    void generar_devuelvePrefijoYOchoCaracteresSinAmbiguos() {
        // Act
        String codigo = generador.generar();

        // Assert
        assertTrue(codigo.matches("BOL-[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}"), codigo);
    }

    @Test
    void generar_noRepiteCodigosEnMilIntentos() {
        // Arrange
        Set<String> codigos = new HashSet<>();

        // Act
        for (int i = 0; i < 1000; i++) {
            codigos.add(generador.generar());
        }

        // Assert
        assertEquals(1000, codigos.size());
    }
}
