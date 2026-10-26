package org.transportsgt.reportingservice.services.comun;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.request.FormatoReporte;
import org.transportsgt.reportingservice.exception.FormatoNoSoportadoException;
import org.transportsgt.reportingservice.exception.IntervaloInvalidoException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidadorFiltrosTest {

    private ValidadorFiltros validador;
    private LocalDate ayer;
    private LocalDate hoy;

    @BeforeEach
    void setUp() {
        validador = new ValidadorFiltros();
        hoy = LocalDate.parse("2026-10-24");
        ayer = hoy.minusDays(1);
    }

    @Nested
    class Intervalo {

        @Test
        void intervalo_cuandoEsValidoOAbierto_loDevuelve() {
            assertAll(
                    () -> assertEquals(new FiltroFechas(ayer, hoy), validador.intervalo(ayer, hoy)),
                    () -> assertEquals(new FiltroFechas(hoy, hoy), validador.intervalo(hoy, hoy)),
                    () -> assertEquals(new FiltroFechas(null, hoy), validador.intervalo(null, hoy)),
                    () -> assertEquals(new FiltroFechas(ayer, null), validador.intervalo(ayer, null)),
                    () -> assertEquals(new FiltroFechas(null, null), validador.intervalo(null, null))
            );
        }

        @Test
        void intervalo_cuandoDesdeEsPosteriorAHasta_lanza400() {
            IntervaloInvalidoException ex = assertThrows(IntervaloInvalidoException.class,
                    () -> validador.intervalo(hoy, ayer));
            assertEquals("VALIDACION", ex.getCodigo());
        }
    }

    @Nested
    class Formato {

        @Test
        void formato_cuandoFaltaOEsConocido_loResuelveSinImportarMayusculas() {
            assertAll(
                    () -> assertEquals(FormatoReporte.JSON, validador.formato(null)),
                    () -> assertEquals(FormatoReporte.JSON, validador.formato(" ")),
                    () -> assertEquals(FormatoReporte.JSON, validador.formato("JSON")),
                    () -> assertEquals(FormatoReporte.PDF, validador.formato(" pdf "))
            );
        }

        @Test
        void formato_cuandoNoEsJsonNiPdf_lanza400() {
            assertThrows(FormatoNoSoportadoException.class, () -> validador.formato("xlsx"));
        }
    }
}
