package org.transportsgt.reportingservice.services.comun;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.request.FormatoReporte;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.exception.FormatoNoSoportadoException;
import org.transportsgt.reportingservice.fixture.RepGastoFixture;
import org.transportsgt.reportingservice.services.exportacion.GeneradorPdf;
import org.transportsgt.reportingservice.support.Soporte;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TextosYRespuestaTest {

    @Mock
    private GeneradorPdf generadorPdf;

    private TextosReporte textos;
    private RespuestaReporte respuesta;
    private ReporteTabla<String> reporte;

    @BeforeEach
    void setUp() {
        textos = Soporte.textos();
        respuesta = new RespuestaReporte(generadorPdf, Soporte.fechas());
        ArmadorReporteTabla armador = Soporte.armador();
        reporte = armador.tabla("Reporte", armador.filtros(new FiltroFechas(null, null), Map.of()),
                armador.sinAgrupar(List.of("a")), Map.of());
    }

    @Nested
    class Textos {

        @Test
        void textos_reemplazanVaciosYSucursalesNoSincronizadas() {
            assertAll(
                    () -> assertEquals("Xela → Guate", textos.trayecto("Xela", "Guate")),
                    () -> assertEquals("— → —", textos.trayecto(null, " ")),
                    () -> assertEquals(TextosReporte.SUCURSAL_DESCONOCIDA, textos.sucursal(null)),
                    () -> assertEquals("Xela", textos.sucursal("Xela"))
            );
        }

        @Test
        void filasTaller_reemplazaPlacaYSucursalFaltantes() {
            // Arrange
            RepGastoFixture.Taller sinPlaca = new RepGastoFixture.Taller("Motor", UUID.randomUUID(), null, null,
                    null, null, null, java.math.BigDecimal.TEN);

            // Act
            var fila = Soporte.filasTaller().de(List.of(sinPlaca)).getFirst();

            // Assert
            assertAll(
                    () -> assertEquals(TextosReporte.SIN_DATO, fila.placa()),
                    () -> assertEquals(TextosReporte.SUCURSAL_DESCONOCIDA, fila.sucursal())
            );
        }
    }

    @Nested
    class Responder {

        @Test
        void responder_json_devuelveElReporteSinTocarElPdf() {
            // Act
            ResponseEntity<?> entidad = respuesta.responder(reporte, FormatoReporte.JSON, "ingresos");

            // Assert
            assertSame(reporte, entidad.getBody());
            verifyNoInteractions(generadorPdf);
        }

        @Test
        void responder_pdf_devuelveAdjuntoConNombreYFecha() {
            // Arrange
            byte[] pdf = {37, 80, 68, 70};
            when(generadorPdf.generar("ingresos", reporte)).thenReturn(pdf);

            // Act
            ResponseEntity<?> entidad = respuesta.responder(reporte, FormatoReporte.PDF, "ingresos");

            // Assert
            assertAll(
                    () -> assertEquals(MediaType.APPLICATION_PDF, entidad.getHeaders().getContentType()),
                    () -> assertEquals("attachment; filename=\"reporte-ingresos-2026-10-24.pdf\"",
                            entidad.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)),
                    () -> assertArrayEquals(pdf, (byte[]) entidad.getBody())
            );
        }

    }
}
