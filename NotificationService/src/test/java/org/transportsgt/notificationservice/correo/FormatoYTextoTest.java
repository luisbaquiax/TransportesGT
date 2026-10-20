package org.transportsgt.notificationservice.correo;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatoYTextoTest {

    private final FormatoCorreo formato = new FormatoCorreo();
    private final TextoPlano textoPlano = new TextoPlano();

    @Nested
    class Dinero {

        @Test
        void dinero_cuandoHayMontoONulo_usaQuetzalesConDosDecimalesYMiles() {
            assertAll(
                    () -> assertEquals("Q 170.00", formato.dinero(new BigDecimal("170"))),
                    () -> assertEquals("Q 3,800.50", formato.dinero(new BigDecimal("3800.5"))),
                    () -> assertEquals("Q 0.13", formato.dinero(new BigDecimal("0.125"))),
                    () -> assertEquals("—", formato.dinero(null))
            );
        }
    }

    @Nested
    class Fechas {

        @Test
        void fechaHora_cuandoRecibeInstantes_losConvierteAHoraDeGuatemala() {
            assertAll(
                    () -> assertEquals("20/10/2026 10:00", formato.fechaHora(Instant.parse("2026-10-20T16:00:00Z"))),
                    () -> assertEquals("10/11/2026 07:00",
                            formato.fechaHora(OffsetDateTime.parse("2026-11-10T13:00:00Z"))),
                    () -> assertEquals("10/11/2026 07:00",
                            formato.fechaHora(ZonedDateTime.parse("2026-11-10T13:00:00Z"))),
                    () -> assertEquals("—", formato.fechaHora(null)),
                    () -> assertEquals("—", formato.fechaHora("no es fecha"))
            );
        }

        @Test
        void fecha_cuandoHayFechaONula_usaDiaMesAnio() {
            assertAll(
                    () -> assertEquals("15/03/2027", formato.fecha(LocalDate.parse("2027-03-15"))),
                    () -> assertEquals("—", formato.fecha(null))
            );
        }
    }

    @Nested
    class DesdeHtml {

        @Test
        void desdeHtml_cuandoHayEtiquetasYEnlaces_dejaTextoConLosEnlacesVisibles() {
            // Arrange
            String html = """
                    <html><head><title>Asunto</title><style>p{}</style></head><body>
                    <p>Hola, <strong>Ana</strong>:</p>
                    <table><tr><td>Total</td><td>Q 170.00</td></tr></table>


                    <p><a href="https://x.gt/activar?token=abc">Activar</a></p>
                    <p>A &amp; B &lt;ok&gt; &quot;c&quot; &#39;d&#39;&nbsp;fin</p>
                    </body></html>""";

            // Act
            String texto = textoPlano.desdeHtml(html);

            // Assert
            assertEquals("Hola, Ana:\n\nTotal Q 170.00\n\nActivar: https://x.gt/activar?token=abc\n\nA & B <ok> \"c\" 'd' fin",
                    texto);
        }
    }
}
