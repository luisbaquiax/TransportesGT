package org.transportsgt.notificationservice.correo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.transportsgt.eventos.payload.boletos.Canal;
import org.transportsgt.eventos.payload.identidad.MotivoContrasena;
import org.transportsgt.eventos.payload.identidad.PropositoContrasena;
import org.transportsgt.eventos.payload.costos.TipoReferencia;
import org.transportsgt.notificationservice.fixture.ChoferLicenciaFixture;
import org.transportsgt.notificationservice.fixture.ContactoFixture;
import org.transportsgt.notificationservice.fixture.EventoFixture;
import org.transportsgt.notificationservice.models.TipoNotificacion;
import org.transportsgt.notificationservice.services.bandeja.Destinatario;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Con el motor real (SpEL, como en ejecución) y las plantillas de src/main/resources.
 */
class ThymeleafRenderizadorCorreoTest {

    private ThymeleafRenderizadorCorreo renderizador;
    private Destinatario destinatario;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolutor = new ClassLoaderTemplateResolver();
        resolutor.setPrefix("templates/");
        resolutor.setSuffix(".html");
        resolutor.setTemplateMode(TemplateMode.HTML);
        resolutor.setCharacterEncoding("UTF-8");
        SpringTemplateEngine motor = new SpringTemplateEngine();
        motor.setTemplateResolver(resolutor);
        renderizador = new ThymeleafRenderizadorCorreo(motor, new FormatoCorreo());
        destinatario = ContactoFixture.destinatario(UUID.randomUUID());
    }

    private CorreoRenderizado render(TipoNotificacion tipo, Map<String, Object> variables) {
        Map<String, Object> todas = new HashMap<>(variables);
        todas.put("destinatario", destinatario);
        return renderizador.renderizar(tipo, todas);
    }

    private static void contiene(CorreoRenderizado correo, String... textos) {
        for (String texto : textos) {
            assertTrue(correo.html().contains(texto), () -> "Falta \"" + texto + "\" en:\n" + correo.html());
        }
    }

    @Nested
    class Renderizar {

        @Test
        void renderizar_cuandoSeUsaCadaTipo_todasUsanElLayoutComun() {
            // Arrange
            Set<TipoNotificacion> probados = EnumSet.noneOf(TipoNotificacion.class);
            Map<TipoNotificacion, Map<String, Object>> datos = Map.ofEntries(
                    Map.entry(TipoNotificacion.COMPRA_BOLETOS, Map.of("datos", EventoFixture.venta(UUID.randomUUID()))),
                    Map.entry(TipoNotificacion.BOLETOS_ANULADOS, Map.of("datos",
                            EventoFixture.anulacion(UUID.randomUUID(), Canal.EN_LINEA, null), "reembolso", false)),
                    Map.entry(TipoNotificacion.CARTERA_RECARGADA, Map.of("datos", EventoFixture.recarga(UUID.randomUUID()))),
                    Map.entry(TipoNotificacion.CARTERA_REEMBOLSADA, Map.of("datos",
                            EventoFixture.reembolso(UUID.randomUUID(), TipoReferencia.BOLETOS), "referencia", "x")),
                    Map.entry(TipoNotificacion.ALQUILER_SOLICITADO, Map.of("datos", EventoFixture.alquilerSolicitado(null))),
                    Map.entry(TipoNotificacion.ALQUILER_COTIZADO, Map.of("datos", EventoFixture.alquilerCotizado(null))),
                    Map.entry(TipoNotificacion.ALQUILER_CONFIRMADO, Map.of("datos",
                            EventoFixture.alquilerConfirmado(null), "paraAdmin", false)),
                    Map.entry(TipoNotificacion.ALQUILER_CANCELADO, Map.of("datos",
                            EventoFixture.alquilerCancelado(null, null), "reembolso", false)),
                    Map.entry(TipoNotificacion.ENLACE_CONTRASENA, Map.of("activacion", false, "enlace", "https://x.gt",
                            "expiraEn", Instant.parse("2026-10-20T16:30:00Z"))),
                    Map.entry(TipoNotificacion.CONTRASENA_CAMBIADA, Map.of("datos",
                            EventoFixture.contrasenaCambiada(MotivoContrasena.CAMBIO))),
                    Map.entry(TipoNotificacion.LICENCIA_POR_VENCER, Map.of("chofer",
                            ChoferLicenciaFixture.chofer(UUID.randomUUID()), "dias", 30L,
                            "fechaVencimiento", ChoferLicenciaFixture.VENCIMIENTO, "paraAdmin", false)));

            // Act & Assert
            for (TipoNotificacion tipo : TipoNotificacion.values()) {
                CorreoRenderizado correo = render(tipo, datos.get(tipo));
                assertAll(tipo.name(),
                        () -> assertFalse(correo.asunto().isBlank()),
                        () -> contiene(correo, "Transportes GT", "Hola, <strong>" + ContactoFixture.NOMBRE,
                                "Este es un correo automático, por favor no lo respondas."));
                probados.add(tipo);
            }
            assertEquals(EnumSet.allOf(TipoNotificacion.class), probados);
        }

        @Test
        void renderizar_cuandoEsCompraDeBoletos_muestraAsientosYMontosEnQuetzales() {
            // Act
            CorreoRenderizado correo = render(TipoNotificacion.COMPRA_BOLETOS,
                    Map.of("datos", EventoFixture.venta(UUID.randomUUID())));

            // Assert
            assertEquals("Confirmación de compra de boletos", correo.asunto());
            contiene(correo, "12, 13", "Q 85.00", "Q 170.00", "20/10/2026");
        }

        @Test
        void renderizar_cuandoEsRecarga_muestraElSaldoPosterior() {
            // Act
            CorreoRenderizado correo = render(TipoNotificacion.CARTERA_RECARGADA,
                    Map.of("datos", EventoFixture.recarga(UUID.randomUUID())));

            // Assert
            contiene(correo, "Q 200.00", "Q 350.00");
        }

        @Test
        void renderizar_cuandoHayOnoReembolso_loIndicaSoloSiCorresponde() {
            // Act
            CorreoRenderizado conReembolso = render(TipoNotificacion.BOLETOS_ANULADOS, Map.of("datos",
                    EventoFixture.anulacion(UUID.randomUUID(), Canal.EN_LINEA, UUID.randomUUID()), "reembolso", true));
            CorreoRenderizado sinReembolso = render(TipoNotificacion.ALQUILER_CANCELADO, Map.of("datos",
                    EventoFixture.alquilerCancelado(UUID.randomUUID(), null), "reembolso", false));

            // Assert
            contiene(conReembolso, "se acreditará en tu cartera digital");
            assertFalse(sinReembolso.html().contains("se acreditará en tu cartera digital"));
        }

        @Test
        void renderizar_cuandoAlquilerConfirmadoEsParaAdmin_cambiaElAsunto() {
            // Act
            CorreoRenderizado admin = render(TipoNotificacion.ALQUILER_CONFIRMADO,
                    Map.of("datos", EventoFixture.alquilerConfirmado(null), "paraAdmin", true));

            // Assert
            assertEquals("Alquiler confirmado: asigna bus y chofer", admin.asunto());
            contiene(admin, "Quetzaltenango → Antigua Guatemala (ida y vuelta)", "Q 3,800.00", "10/11/2026 07:00");
        }

        @Test
        void renderizar_enlace_muestraElBotonYLaVigenciaEnHoraDeGuatemala() {
            // Act
            CorreoRenderizado activacion = render(TipoNotificacion.ENLACE_CONTRASENA, Map.of("activacion", true,
                    "enlace", "http://localhost:5173/activar-cuenta?token=abc", "expiraEn",
                    EventoFixture.enlace(PropositoContrasena.ACTIVACION).expiraEn()));

            // Assert
            assertEquals("Activa tu cuenta de Transportes GT", activacion.asunto());
            contiene(activacion, "href=\"http://localhost:5173/activar-cuenta?token=abc\"", "Activar mi cuenta",
                    "20/10/2026 10:30");
        }

        @Test
        void renderizar_cuandoEsLicenciaParaAdmin_nombraAlChofer() {
            // Act
            CorreoRenderizado admin = render(TipoNotificacion.LICENCIA_POR_VENCER, Map.of("chofer",
                    ChoferLicenciaFixture.chofer(UUID.randomUUID()), "dias", 15L, "fechaVencimiento",
                    ChoferLicenciaFixture.VENCIMIENTO, "paraAdmin", true));

            // Assert
            assertEquals("Licencia por vencer en 15 días", admin.asunto());
            contiene(admin, "La licencia del chofer Juan Pérez vence en 15 días.", "15/03/2027");
        }

        @Test
        void renderizar_cuandoLaPlantillaNoTieneTitulo_falla() {
            // Arrange
            ITemplateEngine motor = mock(ITemplateEngine.class);
            when(motor.process(anyString(), any())).thenReturn("<html><body>sin título</body></html>");
            ThymeleafRenderizadorCorreo sinTitulo = new ThymeleafRenderizadorCorreo(motor, new FormatoCorreo());

            // Act & Assert
            assertThrows(IllegalStateException.class,
                    () -> sinTitulo.renderizar(TipoNotificacion.COMPRA_BOLETOS, Map.of()));
        }

        @Test
        void plantilla_cuandoRecibeUnTipo_usaSuNombreEnMinusculasConGuiones() {
            assertEquals("correo/licencia-por-vencer", ThymeleafRenderizadorCorreo.plantilla(TipoNotificacion.LICENCIA_POR_VENCER));
        }
    }
}
