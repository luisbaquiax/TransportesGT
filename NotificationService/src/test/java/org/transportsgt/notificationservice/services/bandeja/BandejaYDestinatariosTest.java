package org.transportsgt.notificationservice.services.bandeja;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.notificationservice.correo.CorreoRenderizado;
import org.transportsgt.notificationservice.correo.RenderizadorCorreo;
import org.transportsgt.notificationservice.exception.ContactoNoDisponibleException;
import org.transportsgt.notificationservice.fixture.ContactoFixture;
import org.transportsgt.notificationservice.fixture.PropiedadesFixture;
import org.transportsgt.notificationservice.models.Contacto;
import org.transportsgt.notificationservice.models.TipoNotificacion;
import org.transportsgt.notificationservice.repositories.ContactoRepository;
import org.transportsgt.notificationservice.repositories.NotificacionRepository;
import org.transportsgt.notificationservice.services.bandeja.impl.BandejaSalidaImpl;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BandejaYDestinatariosTest {

    @Nested
    class Encolar {

        @Mock
        private RenderizadorCorreo renderizador;
        @Mock
        private NotificacionRepository notificacionRepository;

        private BandejaSalidaImpl bandeja;
        private Destinatario destinatario;
        private UUID idEvento;
        private SolicitudCorreo solicitud;

        @BeforeEach
        void setUp() {
            bandeja = new BandejaSalidaImpl(renderizador, notificacionRepository, PropiedadesFixture.fechas());
            destinatario = ContactoFixture.destinatario(UUID.randomUUID());
            idEvento = UUID.randomUUID();
            solicitud = new SolicitudCorreo(TipoNotificacion.CARTERA_RECARGADA, destinatario, Map.of("datos", "x"),
                    "evt:" + idEvento + ":" + destinatario.correo(), idEvento);
        }

        @Test
        void encolar_cuandoEsNuevo_renderizaYGuardaPendienteListoYa() {
            // Arrange
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
            when(renderizador.renderizar(eq(TipoNotificacion.CARTERA_RECARGADA), variables.capture()))
                    .thenReturn(new CorreoRenderizado("Recarga de cartera acreditada", "<html>…</html>"));
            when(notificacionRepository.insertarPendiente(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(1);

            // Act
            boolean encolado = bandeja.encolar(solicitud);

            // Assert
            assertAll(
                    () -> assertTrue(encolado),
                    () -> assertEquals(destinatario, variables.getValue().get("destinatario")),
                    () -> assertEquals("x", variables.getValue().get("datos"))
            );
            verify(notificacionRepository).insertarPendiente("CARTERA_RECARGADA", destinatario.correo(),
                    destinatario.idUsuario(), "Recarga de cartera acreditada", "<html>…</html>", solicitud.claveDedup(),
                    idEvento, OffsetDateTime.parse("2026-10-20T10:00-06:00"));
        }

        @Test
        void encolar_cuandoLaClaveYaExiste_devuelveFalse() {
            // Arrange
            when(renderizador.renderizar(any(), any())).thenReturn(new CorreoRenderizado("A", "<html/>"));
            when(notificacionRepository.insertarPendiente(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(0);

            // Act & Assert
            assertFalse(bandeja.encolar(solicitud));
        }
    }

    @Nested
    class DestinatariosTest {

        @Mock
        private ContactoRepository contactoRepository;

        private Destinatarios destinatarios;
        private UUID idUsuario;

        @BeforeEach
        void setUp() {
            destinatarios = new Destinatarios(contactoRepository);
            idUsuario = UUID.randomUUID();
        }

        @Test
        void usuario_cuandoExisteAunqueEsteInactivo_loDevuelve() {
            // Arrange
            Contacto inactivo = ContactoFixture.cliente(idUsuario);
            inactivo.setActivo(false);
            when(contactoRepository.findById(idUsuario)).thenReturn(Optional.of(inactivo));

            // Act
            Destinatario destinatario = destinatarios.usuario(idUsuario);

            // Assert
            assertEquals(new Destinatario(idUsuario, inactivo.getCorreo(), ContactoFixture.NOMBRE), destinatario);
        }

        @Test
        void usuario_cuandoNoHayContacto_lanzaErrorTransitorio() {
            // Arrange
            when(contactoRepository.findById(idUsuario)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ContactoNoDisponibleException.class, () -> destinatarios.usuario(idUsuario));
        }

        @Test
        void adminsDeSucursal_cuandoHayAdministradores_devuelveLosActivosDeLaSucursal() {
            // Arrange
            Contacto admin = ContactoFixture.adminSucursal(UUID.randomUUID(), ContactoFixture.SUCURSAL_XELA);
            when(contactoRepository.findAllByIdSucursalAndRolAndActivoTrue(ContactoFixture.SUCURSAL_XELA,
                    Rol.ADMIN_SUCURSAL)).thenReturn(List.of(admin));

            // Act
            List<Destinatario> admins = destinatarios.adminsDeSucursal(ContactoFixture.SUCURSAL_XELA);

            // Assert
            assertEquals(List.of(new Destinatario(admin.getIdUsuario(), admin.getCorreo(), admin.getNombreCompleto())),
                    admins);
        }
    }

    @Nested
    class Claves {

        private final ClaveDedup claveDedup = new ClaveDedup();

        @Test
        void eventoYLicencia_cuandoElCorreoTraeMayusculas_usanElPrefijoYMinusculas() {
            // Arrange
            UUID id = UUID.fromString("7c1d2e3f-1111-4a2b-9c3d-000000000001");

            // Act & Assert
            assertAll(
                    () -> assertEquals("evt:7c1d2e3f-1111-4a2b-9c3d-000000000001:ana@gmail.com",
                            claveDedup.evento(id, "Ana@Gmail.com")),
                    () -> assertEquals("lic:7c1d2e3f-1111-4a2b-9c3d-000000000001:juan@x.gt",
                            claveDedup.licencia(id, "JUAN@x.gt"))
            );
        }
    }
}
