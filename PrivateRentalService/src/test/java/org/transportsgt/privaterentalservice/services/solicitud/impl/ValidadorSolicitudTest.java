package org.transportsgt.privaterentalservice.services.solicitud.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.privaterentalservice.dto.request.SolicitudAlquilerRequest;
import org.transportsgt.privaterentalservice.exception.ReglaNegocioException;
import org.transportsgt.privaterentalservice.exception.SolicitudInvalidaException;
import org.transportsgt.privaterentalservice.repositories.SucursalRefRepository;
import org.transportsgt.privaterentalservice.services.comun.CalendarioGuatemala;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidadorSolicitudTest {

    private static final OffsetDateTime AHORA = OffsetDateTime.parse("2026-10-20T08:00:00-06:00");
    private static final OffsetDateTime SALIDA = AHORA.plusDays(10);

    @Mock
    private SucursalRefRepository sucursalRepository;
    @Mock
    private CalendarioGuatemala calendario;

    private ValidadorSolicitud validador;

    @BeforeEach
    void setUp() {
        validador = new ValidadorSolicitud(sucursalRepository, calendario);
    }

    private static SolicitudAlquilerRequest solicitud(Boolean idaYVuelta, OffsetDateTime retorno,
                                                      String latO, String lonO, String latD, String lonD) {
        return new SolicitudAlquilerRequest(UUID.randomUUID(), "Xela", "Antigua", SALIDA, idaYVuelta, retorno, 20,
                dec(latO), dec(lonO), dec(latD), dec(lonD), new BigDecimal("180.00"));
    }

    private static BigDecimal dec(String valor) {
        return valor == null ? null : new BigDecimal(valor);
    }

    private static SolicitudAlquilerRequest fechas(Boolean idaYVuelta, OffsetDateTime retorno) {
        return solicitud(idaYVuelta, retorno, null, null, null, null);
    }

    private String codigoCoordenadas(String latO, String lonO, String latD, String lonD) {
        return assertThrows(ReglaNegocioException.class,
                () -> validador.validar(solicitud(false, null, latO, lonO, latD, lonD))).getCodigo();
    }

    @Test
    void fechasValidasConYSinRetorno() {
        when(calendario.ahora()).thenReturn(AHORA);
        assertDoesNotThrow(() -> validador.validar(fechas(false, null)));
        assertDoesNotThrow(() -> validador.validar(fechas(true, SALIDA.plusDays(1))));
    }

    @Test
    void retornoSinIdaYVueltaOAntesDeLaSalidaEsInvalido() {
        assertThrows(SolicitudInvalidaException.class, () -> validador.validar(fechas(false, SALIDA.plusDays(1))));
        assertThrows(SolicitudInvalidaException.class, () -> validador.validar(fechas(true, SALIDA)));
        assertThrows(SolicitudInvalidaException.class, () -> validador.validar(fechas(true, null)));
    }

    @Test
    void coordenadasCompletasYEnRangoPasan() {
        when(calendario.ahora()).thenReturn(AHORA);
        assertDoesNotThrow(() -> validador.validar(solicitud(false, null, "-14.8", "-91.5", "14.5", "180")));
    }

    @Test
    void latitudOLongitudFueraDeRango() {
        when(calendario.ahora()).thenReturn(AHORA);
        assertEquals("COORDENADAS_INVALIDAS", codigoCoordenadas("14.8", "-91.5", "-90.1", "-90.7"));
        assertEquals("COORDENADAS_INVALIDAS", codigoCoordenadas("14.8", "-91.5", "14.5", "180.5"));
        assertEquals("COORDENADAS_INVALIDAS", codigoCoordenadas("14.8", "-181", "14.5", "-90.7"));
    }
}
