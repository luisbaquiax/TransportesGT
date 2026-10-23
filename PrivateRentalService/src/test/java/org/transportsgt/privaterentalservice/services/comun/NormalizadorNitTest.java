package org.transportsgt.privaterentalservice.services.comun;

import org.junit.jupiter.api.Test;
import org.transportsgt.privaterentalservice.exception.SolicitudInvalidaException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NormalizadorNitTest {

    private final NormalizadorNit normalizador = new NormalizadorNit();

    @Test
    void mostradorVacioOCfEsConsumidorFinal() {
        assertEquals("CF", normalizador.paraMostrador(null));
        assertEquals("CF", normalizador.paraMostrador("   "));
        assertEquals("CF", normalizador.paraMostrador("c f"));
    }

    @Test
    void mostradorNormalizaComoBoletosYCartera() {
        assertEquals("1234567K", normalizador.paraMostrador(" 1234567-k "));
        assertEquals("12345678", normalizador.paraMostrador("1234/567 8"));
    }

    @Test
    void mostradorConCaracteresNoValidosEs400() {
        assertThrows(SolicitudInvalidaException.class, () -> normalizador.paraMostrador("12.345"));
        assertThrows(SolicitudInvalidaException.class, () -> normalizador.paraMostrador("1"));
    }

    @Test
    void normalizarVacioEsNull() {
        assertNull(normalizador.normalizar(" - "));
    }
}
