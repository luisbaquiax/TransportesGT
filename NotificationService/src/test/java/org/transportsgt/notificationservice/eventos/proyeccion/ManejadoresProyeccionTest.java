package org.transportsgt.notificationservice.eventos.proyeccion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.EventoNoRecuperableException;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosChofer;
import org.transportsgt.eventos.payload.DatosUsuario;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.notificationservice.eventos.ValidacionEventos;
import org.transportsgt.notificationservice.fixture.ChoferLicenciaFixture;
import org.transportsgt.notificationservice.fixture.EventoFixture;
import org.transportsgt.notificationservice.services.proyeccion.ProyeccionChoferesService;
import org.transportsgt.notificationservice.services.proyeccion.ProyeccionContactosService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManejadoresProyeccionTest {

    @Mock
    private ProyeccionContactosService proyeccionContactos;
    @Mock
    private ProyeccionChoferesService proyeccionChoferes;

    private final ValidacionEventos validacion = new ValidacionEventos();
    private DatosUsuario usuario;
    private DatosChofer chofer;

    @BeforeEach
    void setUp() {
        usuario = EventoFixture.usuario(UUID.randomUUID(), Rol.CLIENTE, null);
        chofer = EventoFixture.chofer(UUID.randomUUID(), ChoferLicenciaFixture.VENCIMIENTO, true);
    }

    @Test
    void tipo_cuandoSeConsulta_cadaManejadorDeclaraSuEvento() {
        assertAll(
                () -> assertEquals(TipoEvento.USUARIO_CREADO, new ManejadorUsuarioCreado(proyeccionContactos, validacion).tipo()),
                () -> assertEquals(TipoEvento.USUARIO_ACTUALIZADO,
                        new ManejadorUsuarioActualizado(proyeccionContactos, validacion).tipo()),
                () -> assertEquals(TipoEvento.CHOFER_REGISTRADO,
                        new ManejadorChoferRegistrado(proyeccionChoferes, validacion).tipo()),
                () -> assertEquals(TipoEvento.CHOFER_ACTUALIZADO,
                        new ManejadorChoferActualizado(proyeccionChoferes, validacion).tipo())
        );
    }

    @Nested
    class Usuarios {

        @Test
        void manejar_cuandoLlegaCreadoOActualizado_proyectaConLaVersionDelSobre() {
            // Arrange
            when(proyeccionContactos.aplicar(usuario, 3L)).thenReturn(true, false);

            // Act
            new ManejadorUsuarioCreado(proyeccionContactos, validacion)
                    .manejar(EventoFixture.sobre(TipoEvento.USUARIO_CREADO, usuario, 3L));
            new ManejadorUsuarioActualizado(proyeccionContactos, validacion)
                    .manejar(EventoFixture.sobre(TipoEvento.USUARIO_ACTUALIZADO, usuario, 3L));

            // Assert
            verify(proyeccionContactos, org.mockito.Mockito.times(2)).aplicar(usuario, 3L);
        }

        @Test
        void manejar_cuandoFaltaElCorreoOLosDatos_esNoRecuperable() {
            // Arrange
            ManejadorUsuarioCreado manejador = new ManejadorUsuarioCreado(proyeccionContactos, validacion);
            DatosUsuario sinCorreo = new DatosUsuario(UUID.randomUUID(), " ", "Ana", Rol.CLIENTE, null, true);
            SobreEvento<DatosUsuario> sinDatos = EventoFixture.sobre(TipoEvento.USUARIO_CREADO, null, 1L);

            // Act & Assert
            assertAll(
                    () -> assertEquals("El evento " + sinDatos.idEvento() + " no trae datos",
                            assertThrows(EventoNoRecuperableException.class, () -> manejador.manejar(sinDatos)).getMessage()),
                    () -> assertThrows(EventoNoRecuperableException.class,
                            () -> manejador.manejar(EventoFixture.sobre(TipoEvento.USUARIO_CREADO, sinCorreo, 1L)))
            );
            verifyNoInteractions(proyeccionContactos);
        }
    }

    @Nested
    class Choferes {

        @Test
        void manejar_cuandoLlegaRegistradoOActualizado_proyectaElChofer() {
            // Arrange
            when(proyeccionChoferes.aplicar(chofer, 1L)).thenReturn(true);

            // Act
            new ManejadorChoferRegistrado(proyeccionChoferes, validacion)
                    .manejar(EventoFixture.sobre(TipoEvento.CHOFER_REGISTRADO, chofer, 1L));
            new ManejadorChoferActualizado(proyeccionChoferes, validacion)
                    .manejar(EventoFixture.sobre(TipoEvento.CHOFER_ACTUALIZADO, chofer, 1L));

            // Assert
            verify(proyeccionChoferes, org.mockito.Mockito.times(2)).aplicar(chofer, 1L);
        }

        @Test
        void manejar_cuandoFaltaLaFechaDeVencimiento_esNoRecuperable() {
            // Arrange
            DatosChofer sinFecha = EventoFixture.chofer(UUID.randomUUID(), null, true);

            // Act & Assert
            assertThrows(EventoNoRecuperableException.class, () -> new ManejadorChoferRegistrado(proyeccionChoferes,
                    validacion).manejar(EventoFixture.sobre(TipoEvento.CHOFER_REGISTRADO, sinFecha, 1L)));
            verifyNoInteractions(proyeccionChoferes);
        }
    }
}
