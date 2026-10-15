package org.transportsgt.customerwalletservice.eventos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.customerwalletservice.fixture.EventoFixture;
import org.transportsgt.customerwalletservice.services.ClienteService;
import org.transportsgt.eventos.EventoNoRecuperableException;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosUsuario;
import org.transportsgt.eventos.payload.Rol;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManejadoresUsuarioTest {

    @Mock
    private ClienteService clienteService;

    private ManejadorUsuarioCreado creado;
    private ManejadorUsuarioActualizado actualizado;
    private UUID idUsuario;

    @BeforeEach
    void setUp() {
        creado = new ManejadorUsuarioCreado(clienteService);
        actualizado = new ManejadorUsuarioActualizado(clienteService);
        idUsuario = UUID.randomUUID();
    }

    @Nested
    class Tipo {

        @Test
        void tipo_devuelveLosEventosDeIdentidadQueConsume() {
            assertAll(
                    () -> assertEquals(TipoEvento.USUARIO_CREADO, creado.tipo()),
                    () -> assertEquals(TipoEvento.USUARIO_ACTUALIZADO, actualizado.tipo())
            );
        }
    }

    @Nested
    class Manejar {

        @Test
        void manejar_cuandoUsuarioCreadoEsValido_delegaConLaVersionDelSobre() {
            // Arrange
            SobreEvento<DatosUsuario> evento = EventoFixture.sobre(TipoEvento.USUARIO_CREADO,
                    EventoFixture.usuario(idUsuario), 0L);
            when(clienteService.aplicarUsuario(evento.datos(), 0L)).thenReturn(true);

            // Act
            creado.manejar(evento);

            // Assert
            verify(clienteService).aplicarUsuario(evento.datos(), 0L);
        }

        @Test
        void manejar_cuandoUsuarioActualizadoSeIgnora_noFalla() {
            // Arrange
            SobreEvento<DatosUsuario> evento = EventoFixture.sobre(TipoEvento.USUARIO_ACTUALIZADO,
                    EventoFixture.usuario(idUsuario), 5L);
            when(clienteService.aplicarUsuario(evento.datos(), 5L)).thenReturn(false);

            // Act
            actualizado.manejar(evento);

            // Assert
            verify(clienteService).aplicarUsuario(evento.datos(), 5L);
        }

        @Test
        void manejar_cuandoFaltanDatosObligatorios_lanzaEventoNoRecuperableSinAplicar() {
            // Arrange
            SobreEvento<DatosUsuario> sinId = EventoFixture.sobre(TipoEvento.USUARIO_CREADO,
                    new DatosUsuario(null, "a@b.gt", "Ana", Rol.CLIENTE, null, true), 0L);
            SobreEvento<DatosUsuario> sinCorreo = EventoFixture.sobre(TipoEvento.USUARIO_CREADO,
                    new DatosUsuario(idUsuario, " ", "Ana", Rol.CLIENTE, null, true), 0L);
            SobreEvento<DatosUsuario> sinNombre = EventoFixture.sobre(TipoEvento.USUARIO_ACTUALIZADO,
                    new DatosUsuario(idUsuario, "a@b.gt", null, Rol.CLIENTE, null, true), 1L);

            // Act & Assert
            assertAll(
                    () -> assertThrows(EventoNoRecuperableException.class, () -> creado.manejar(sinId)),
                    () -> assertThrows(EventoNoRecuperableException.class, () -> creado.manejar(sinCorreo)),
                    () -> assertThrows(EventoNoRecuperableException.class, () -> actualizado.manejar(sinNombre))
            );
            verifyNoInteractions(clienteService);
        }
    }
}
