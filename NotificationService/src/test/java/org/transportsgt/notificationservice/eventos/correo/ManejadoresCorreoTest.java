package org.transportsgt.notificationservice.eventos.correo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.mensajeria.excepciones.EventoNoRecuperableException;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.boletos.Canal;
import org.transportsgt.eventos.payload.alquileres.DatosAlquilerConfirmado;
import org.transportsgt.eventos.payload.boletos.DatosBoletosAnulados;
import org.transportsgt.eventos.payload.identidad.DatosContrasenaCambiada;
import org.transportsgt.eventos.payload.identidad.DatosEnlaceContrasena;
import org.transportsgt.eventos.payload.boletos.DatosVentaBoletos;
import org.transportsgt.eventos.payload.identidad.MotivoContrasena;
import org.transportsgt.eventos.payload.identidad.PropositoContrasena;
import org.transportsgt.eventos.payload.costos.TipoReferencia;
import org.transportsgt.notificationservice.eventos.ValidacionEventos;
import org.transportsgt.notificationservice.exception.ContactoNoDisponibleException;
import org.transportsgt.notificationservice.fixture.ContactoFixture;
import org.transportsgt.notificationservice.fixture.EventoFixture;
import org.transportsgt.notificationservice.models.TipoNotificacion;
import org.transportsgt.notificationservice.services.bandeja.BandejaSalida;
import org.transportsgt.notificationservice.services.bandeja.ClaveDedup;
import org.transportsgt.notificationservice.services.bandeja.Destinatario;
import org.transportsgt.notificationservice.services.bandeja.Destinatarios;
import org.transportsgt.notificationservice.services.bandeja.SolicitudCorreo;
import org.transportsgt.notificationservice.services.enlace.EnlaceContrasenaService;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManejadoresCorreoTest {

    @Mock
    private Destinatarios destinatarios;
    @Mock
    private BandejaSalida bandeja;

    private final ValidacionEventos validacion = new ValidacionEventos();
    private CorreosDeEvento correos;
    private UUID idUsuario;
    private Destinatario cliente;
    private Destinatario admin;

    @BeforeEach
    void setUp() {
        correos = new CorreosDeEvento(bandeja, new ClaveDedup());
        idUsuario = UUID.randomUUID();
        cliente = ContactoFixture.destinatario(idUsuario);
        admin = ContactoFixture.destinatario(UUID.randomUUID());
    }

    private List<SolicitudCorreo> encolados(int veces) {
        ArgumentCaptor<SolicitudCorreo> captor = ArgumentCaptor.forClass(SolicitudCorreo.class);
        verify(bandeja, times(veces)).encolar(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void tipo_cuandoSeConsulta_cadaManejadorDeclaraSuEvento() {
        assertAll(
                () -> assertEquals(TipoEvento.VENTA_BOLETOS_REGISTRADA,
                        new ManejadorVentaBoletosRegistrada(destinatarios, correos, validacion).tipo()),
                () -> assertEquals(TipoEvento.BOLETOS_ANULADOS,
                        new ManejadorBoletosAnulados(destinatarios, correos, validacion).tipo()),
                () -> assertEquals(TipoEvento.CARTERA_RECARGADA,
                        new ManejadorCarteraRecargada(destinatarios, correos, validacion).tipo()),
                () -> assertEquals(TipoEvento.CARTERA_REEMBOLSADA,
                        new ManejadorCarteraReembolsada(destinatarios, correos, validacion).tipo()),
                () -> assertEquals(TipoEvento.ALQUILER_SOLICITADO,
                        new ManejadorAlquilerSolicitado(destinatarios, correos, validacion).tipo()),
                () -> assertEquals(TipoEvento.ALQUILER_COTIZADO,
                        new ManejadorAlquilerCotizado(destinatarios, correos, validacion).tipo()),
                () -> assertEquals(TipoEvento.ALQUILER_CONFIRMADO,
                        new ManejadorAlquilerConfirmado(destinatarios, correos, validacion).tipo()),
                () -> assertEquals(TipoEvento.ALQUILER_CANCELADO,
                        new ManejadorAlquilerCancelado(destinatarios, correos, validacion).tipo()),
                () -> assertEquals(TipoEvento.CONTRASENA_CAMBIADA,
                        new ManejadorContrasenaCambiada(destinatarios, correos, validacion).tipo())
        );
    }

    @Nested
    class Boletos {

        @Test
        void ventaRegistrada_cuandoHayComprador_encolaLaConfirmacionConClaveDelEvento() {
            // Arrange
            DatosVentaBoletos venta = EventoFixture.venta(idUsuario);
            SobreEvento<DatosVentaBoletos> evento = EventoFixture.sobre(TipoEvento.VENTA_BOLETOS_REGISTRADA, venta, 0L);
            when(destinatarios.usuario(idUsuario)).thenReturn(cliente);
            when(bandeja.encolar(any())).thenReturn(true);

            // Act
            new ManejadorVentaBoletosRegistrada(destinatarios, correos, validacion).manejar(evento);

            // Assert
            SolicitudCorreo solicitud = encolados(1).getFirst();
            assertAll(
                    () -> assertEquals(TipoNotificacion.COMPRA_BOLETOS, solicitud.tipo()),
                    () -> assertEquals(cliente, solicitud.destinatario()),
                    () -> assertEquals(venta, solicitud.variables().get("datos")),
                    () -> assertEquals("evt:" + evento.idEvento() + ":" + cliente.correo(), solicitud.claveDedup()),
                    () -> assertEquals(evento.idEvento(), solicitud.idEventoOrigen())
            );
        }

        @Test
        void ventaRegistrada_cuandoEsDeMostradorSinCuenta_noEncola() {
            // Act
            new ManejadorVentaBoletosRegistrada(destinatarios, correos, validacion)
                    .manejar(EventoFixture.sobre(TipoEvento.VENTA_BOLETOS_REGISTRADA, EventoFixture.venta(null), 0L));

            // Assert
            verifyNoInteractions(bandeja, destinatarios);
        }

        @Test
        void ventaRegistrada_cuandoElCompradorNoTieneContactoAun_propagaElErrorTransitorio() {
            // Arrange
            when(destinatarios.usuario(idUsuario)).thenThrow(new ContactoNoDisponibleException(idUsuario));

            // Act & Assert
            assertThrows(ContactoNoDisponibleException.class, () -> new ManejadorVentaBoletosRegistrada(destinatarios,
                    correos, validacion).manejar(EventoFixture.sobre(TipoEvento.VENTA_BOLETOS_REGISTRADA,
                    EventoFixture.venta(idUsuario), 0L)));
        }

        @Test
        void manejarBoletosAnulados_cuandoFueEnLineaConPago_indicaElReembolso() {
            // Arrange
            ManejadorBoletosAnulados manejador = new ManejadorBoletosAnulados(destinatarios, correos, validacion);
            when(destinatarios.usuario(idUsuario)).thenReturn(cliente);
            DatosBoletosAnulados conPago = EventoFixture.anulacion(idUsuario, Canal.EN_LINEA, UUID.randomUUID());
            DatosBoletosAnulados sinPago = EventoFixture.anulacion(idUsuario, Canal.EN_LINEA, null);
            DatosBoletosAnulados mostrador = EventoFixture.anulacion(idUsuario, Canal.MOSTRADOR, null);

            // Act
            manejador.manejar(EventoFixture.sobre(TipoEvento.BOLETOS_ANULADOS, conPago, 1L));
            manejador.manejar(EventoFixture.sobre(TipoEvento.BOLETOS_ANULADOS, sinPago, 1L));
            manejador.manejar(EventoFixture.sobre(TipoEvento.BOLETOS_ANULADOS, mostrador, 1L));
            manejador.manejar(EventoFixture.sobre(TipoEvento.BOLETOS_ANULADOS,
                    EventoFixture.anulacion(null, Canal.MOSTRADOR, null), 1L));

            // Assert
            List<SolicitudCorreo> solicitudes = encolados(3);
            assertAll(
                    () -> assertEquals(true, solicitudes.get(0).variables().get("reembolso")),
                    () -> assertEquals(false, solicitudes.get(1).variables().get("reembolso")),
                    () -> assertEquals(false, solicitudes.get(2).variables().get("reembolso"))
            );
        }
    }

    @Nested
    class Cartera {

        @Test
        void manejarCartera_cuandoHayRecargaOReembolso_avisaAlDuenoDeLaCartera() {
            // Arrange
            when(destinatarios.usuario(idUsuario)).thenReturn(cliente);

            // Act
            new ManejadorCarteraRecargada(destinatarios, correos, validacion)
                    .manejar(EventoFixture.sobre(TipoEvento.CARTERA_RECARGADA, EventoFixture.recarga(idUsuario), 1L));
            new ManejadorCarteraReembolsada(destinatarios, correos, validacion).manejar(EventoFixture.sobre(
                    TipoEvento.CARTERA_REEMBOLSADA, EventoFixture.reembolso(idUsuario, TipoReferencia.ALQUILER), 2L));
            new ManejadorCarteraReembolsada(destinatarios, correos, validacion).manejar(EventoFixture.sobre(
                    TipoEvento.CARTERA_REEMBOLSADA, EventoFixture.reembolso(idUsuario, TipoReferencia.BOLETOS), 3L));

            // Assert
            List<SolicitudCorreo> solicitudes = encolados(3);
            assertAll(
                    () -> assertEquals(TipoNotificacion.CARTERA_RECARGADA, solicitudes.get(0).tipo()),
                    () -> assertEquals("la cancelación de tu alquiler", solicitudes.get(1).variables().get("referencia")),
                    () -> assertEquals("la anulación de tu compra de boletos", solicitudes.get(2).variables().get("referencia"))
            );
        }

        @Test
        void recarga_sinSaldoPosterior_esNoRecuperable() {
            // Arrange
            var recarga = new org.transportsgt.eventos.payload.cartera.DatosCarteraRecargada(UUID.randomUUID(), UUID.randomUUID(),
                    idUsuario, java.math.BigDecimal.TEN, null, null);

            // Act & Assert
            assertThrows(EventoNoRecuperableException.class, () -> new ManejadorCarteraRecargada(destinatarios, correos,
                    validacion).manejar(EventoFixture.sobre(TipoEvento.CARTERA_RECARGADA, recarga, 1L)));
        }
    }

    @Nested
    class Alquileres {

        @Test
        void manejarAlquilerSolicitado_cuandoHayAdmins_avisaACadaUno() {
            // Arrange
            when(destinatarios.adminsDeSucursal(ContactoFixture.SUCURSAL_XELA)).thenReturn(List.of(admin, cliente));

            // Act
            new ManejadorAlquilerSolicitado(destinatarios, correos, validacion).manejar(EventoFixture.sobre(
                    TipoEvento.ALQUILER_SOLICITADO, EventoFixture.alquilerSolicitado(idUsuario), 0L));

            // Assert
            assertEquals(List.of(admin, cliente), encolados(2).stream().map(SolicitudCorreo::destinatario).toList());
        }

        @Test
        void manejarAlquilerCotizado_cuandoElClienteTieneCuenta_leAvisaYSinCuentaNo() {
            // Arrange
            ManejadorAlquilerCotizado manejador = new ManejadorAlquilerCotizado(destinatarios, correos, validacion);
            when(destinatarios.usuario(idUsuario)).thenReturn(cliente);

            // Act
            manejador.manejar(EventoFixture.sobre(TipoEvento.ALQUILER_COTIZADO, EventoFixture.alquilerCotizado(idUsuario), 1L));
            manejador.manejar(EventoFixture.sobre(TipoEvento.ALQUILER_COTIZADO, EventoFixture.alquilerCotizado(null), 1L));

            // Assert
            assertEquals(TipoNotificacion.ALQUILER_COTIZADO, encolados(1).getFirst().tipo());
        }

        @Test
        void manejarAlquilerConfirmado_cuandoElClienteTieneCuenta_avisaAlClienteYALosAdmins() {
            // Arrange
            DatosAlquilerConfirmado datos = EventoFixture.alquilerConfirmado(idUsuario);
            when(destinatarios.usuario(idUsuario)).thenReturn(cliente);
            when(destinatarios.adminsDeSucursal(ContactoFixture.SUCURSAL_XELA)).thenReturn(List.of(admin));

            // Act
            new ManejadorAlquilerConfirmado(destinatarios, correos, validacion)
                    .manejar(EventoFixture.sobre(TipoEvento.ALQUILER_CONFIRMADO, datos, 2L));

            // Assert
            List<SolicitudCorreo> solicitudes = encolados(2);
            assertAll(
                    () -> assertEquals(cliente, solicitudes.get(0).destinatario()),
                    () -> assertEquals(false, solicitudes.get(0).variables().get("paraAdmin")),
                    () -> assertEquals(admin, solicitudes.get(1).destinatario()),
                    () -> assertEquals(true, solicitudes.get(1).variables().get("paraAdmin"))
            );
        }

        @Test
        void confirmado_sinCuentaDelCliente_soloAvisaALosAdmins() {
            // Arrange
            when(destinatarios.adminsDeSucursal(ContactoFixture.SUCURSAL_XELA)).thenReturn(List.of(admin));

            // Act
            new ManejadorAlquilerConfirmado(destinatarios, correos, validacion).manejar(EventoFixture.sobre(
                    TipoEvento.ALQUILER_CONFIRMADO, EventoFixture.alquilerConfirmado(null), 2L));

            // Assert
            assertEquals(admin, encolados(1).getFirst().destinatario());
        }

        @Test
        void manejarAlquilerCancelado_cuandoHuboPagoConCartera_indicaElReembolso() {
            // Arrange
            ManejadorAlquilerCancelado manejador = new ManejadorAlquilerCancelado(destinatarios, correos, validacion);
            when(destinatarios.usuario(idUsuario)).thenReturn(cliente);

            // Act
            manejador.manejar(EventoFixture.sobre(TipoEvento.ALQUILER_CANCELADO,
                    EventoFixture.alquilerCancelado(idUsuario, UUID.randomUUID()), 3L));
            manejador.manejar(EventoFixture.sobre(TipoEvento.ALQUILER_CANCELADO,
                    EventoFixture.alquilerCancelado(idUsuario, null), 3L));
            manejador.manejar(EventoFixture.sobre(TipoEvento.ALQUILER_CANCELADO,
                    EventoFixture.alquilerCancelado(null, null), 3L));

            // Assert
            List<SolicitudCorreo> solicitudes = encolados(2);
            assertAll(
                    () -> assertEquals(true, solicitudes.get(0).variables().get("reembolso")),
                    () -> assertEquals(false, solicitudes.get(1).variables().get("reembolso"))
            );
        }
    }

    @Nested
    class Identidad {

        @Mock
        private EnlaceContrasenaService enlaceContrasena;

        @Test
        void manejarContrasenaCambiada_cuandoLlega_usaElCorreoDelPayloadSinConsultarContactos() {
            // Arrange
            DatosContrasenaCambiada datos = EventoFixture.contrasenaCambiada(MotivoContrasena.RESTABLECIMIENTO);

            // Act
            new ManejadorContrasenaCambiada(destinatarios, correos, validacion)
                    .manejar(EventoFixture.sobre(TipoEvento.CONTRASENA_CAMBIADA, datos, null));

            // Assert
            assertEquals(new Destinatario(datos.idUsuario(), "ana.lopez@gmail.com", "Ana López"),
                    encolados(1).getFirst().destinatario());
            verifyNoInteractions(destinatarios);
        }

        @Test
        void manejarEnlace_cuandoLlega_delegaElEnvioDirecto() {
            // Arrange
            DatosEnlaceContrasena datos = EventoFixture.enlace(PropositoContrasena.ACTIVACION);
            SobreEvento<DatosEnlaceContrasena> evento = EventoFixture.sobre(TipoEvento.ENLACE_CONTRASENA_EMITIDO, datos, null);
            ManejadorEnlaceContrasenaEmitido manejador = new ManejadorEnlaceContrasenaEmitido(enlaceContrasena, validacion);
            when(enlaceContrasena.enviar(evento.idEvento(), datos)).thenReturn(true, false);

            // Act
            manejador.manejar(evento);
            manejador.manejar(evento);

            // Assert
            assertEquals(TipoEvento.ENLACE_CONTRASENA_EMITIDO, manejador.tipo());
            verify(enlaceContrasena, times(2)).enviar(evento.idEvento(), datos);
        }

        @Test
        void enlace_sinToken_esNoRecuperableYNoNombraElSecreto() {
            // Arrange
            DatosEnlaceContrasena sinToken = new DatosEnlaceContrasena(UUID.randomUUID(), "ana@gmail.com", "Ana",
                    PropositoContrasena.ACTIVACION, null, EventoFixture.AHORA);

            // Act
            EventoNoRecuperableException ex = assertThrows(EventoNoRecuperableException.class,
                    () -> new ManejadorEnlaceContrasenaEmitido(enlaceContrasena, validacion).manejar(
                            EventoFixture.sobre(TipoEvento.ENLACE_CONTRASENA_EMITIDO, sinToken, null)));

            // Assert
            assertEquals(true, ex.getMessage().endsWith("no trae token"));
            verifyNoInteractions(enlaceContrasena);
        }
    }
}
