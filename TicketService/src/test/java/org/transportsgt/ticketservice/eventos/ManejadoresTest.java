package org.transportsgt.ticketservice.eventos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.ticketservice.fixture.EventoFixture;
import org.transportsgt.ticketservice.fixture.ViajeRefFixture;
import org.transportsgt.ticketservice.models.ViajeRef;
import org.transportsgt.ticketservice.services.AnulacionViajeService;
import org.transportsgt.ticketservice.services.ProyeccionClientesService;
import org.transportsgt.ticketservice.services.ProyeccionViajesService;
import org.transportsgt.eventos.EventoNoRecuperableException;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosPerfilClienteActualizado;
import org.transportsgt.eventos.payload.DatosViaje;
import org.transportsgt.eventos.payload.DatosViajeEliminado;
import org.transportsgt.eventos.payload.EstadoViaje;
import org.transportsgt.eventos.payload.TipoViaje;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManejadoresTest {

    @Mock
    private ProyeccionViajesService proyeccionViajes;
    @Mock
    private ProyeccionClientesService proyeccionClientes;
    @Mock
    private AnulacionViajeService anulacionViaje;

    private final ValidacionEventos validacion = new ValidacionEventos();
    private ViajeRef viaje;

    @BeforeEach
    void setUp() {
        viaje = ViajeRefFixture.programado(UUID.randomUUID());
    }

    private SobreEvento<DatosViaje> sobreViaje(TipoEvento tipo, DatosViaje datos) {
        return EventoFixture.sobre(tipo, datos, 4L);
    }

    @Nested
    class Viajes {

        @Test
        void manejadores_declaranSuTipoDeEvento() {
            assertAll(
                    () -> assertEquals(TipoEvento.VIAJE_PROGRAMADO,
                            new ManejadorViajeProgramado(proyeccionViajes, validacion).tipo()),
                    () -> assertEquals(TipoEvento.VIAJE_ACTUALIZADO,
                            new ManejadorViajeActualizado(proyeccionViajes, validacion).tipo()),
                    () -> assertEquals(TipoEvento.VIAJE_ELIMINADO,
                            new ManejadorViajeEliminado(anulacionViaje, validacion).tipo()),
                    () -> assertEquals(TipoEvento.PERFIL_CLIENTE_ACTUALIZADO,
                            new ManejadorPerfilClienteActualizado(proyeccionClientes, validacion).tipo())
            );
        }

        @Test
        void programadoYActualizado_proyectanElViajeConLaVersionDelSobre() {
            // Arrange
            DatosViaje datos = EventoFixture.viaje(viaje, TipoViaje.REGULAR, EstadoViaje.PROGRAMADO);
            when(proyeccionViajes.aplicar(datos, 4L)).thenReturn(true, false);

            // Act
            new ManejadorViajeProgramado(proyeccionViajes, validacion).manejar(sobreViaje(TipoEvento.VIAJE_PROGRAMADO, datos));
            new ManejadorViajeActualizado(proyeccionViajes, validacion).manejar(sobreViaje(TipoEvento.VIAJE_ACTUALIZADO, datos));

            // Assert
            verify(proyeccionViajes, org.mockito.Mockito.times(2)).aplicar(datos, 4L);
        }

        @Test
        void viaje_cuandoEsAlquilerSinRuta_noLoRechaza() {
            // Arrange
            viaje.setIdRuta(null);
            DatosViaje datos = EventoFixture.viaje(viaje, TipoViaje.ALQUILER_PRIVADO, EstadoViaje.PROGRAMADO);

            // Act & Assert
            assertDoesNotThrow(() -> new ManejadorViajeProgramado(proyeccionViajes, validacion)
                    .manejar(sobreViaje(TipoEvento.VIAJE_PROGRAMADO, datos)));
        }

        @Test
        void viaje_cuandoFaltanDatos_esNoRecuperable() {
            // Arrange
            ManejadorViajeProgramado manejador = new ManejadorViajeProgramado(proyeccionViajes, validacion);
            ViajeRef sinPrecio = ViajeRefFixture.programado(UUID.randomUUID());
            sinPrecio.setPrecioBoleto(null);
            ViajeRef sinDestino = ViajeRefFixture.programado(UUID.randomUUID());
            sinDestino.setNombreDestino(" ");
            ViajeRef sinId = ViajeRefFixture.programado(null);

            // Act & Assert
            assertAll(
                    () -> assertThrows(EventoNoRecuperableException.class, () -> manejador.manejar(sobreViaje(
                            TipoEvento.VIAJE_PROGRAMADO, EventoFixture.viaje(sinPrecio, TipoViaje.REGULAR, EstadoViaje.PROGRAMADO)))),
                    () -> assertThrows(EventoNoRecuperableException.class, () -> manejador.manejar(sobreViaje(
                            TipoEvento.VIAJE_PROGRAMADO, EventoFixture.viaje(sinDestino, TipoViaje.REGULAR, EstadoViaje.PROGRAMADO)))),
                    () -> assertThrows(EventoNoRecuperableException.class, () -> manejador.manejar(sobreViaje(
                            TipoEvento.VIAJE_PROGRAMADO, EventoFixture.viaje(sinId, TipoViaje.REGULAR, EstadoViaje.PROGRAMADO)))),
                    () -> assertThrows(EventoNoRecuperableException.class, () -> manejador.manejar(sobreViaje(
                            TipoEvento.VIAJE_PROGRAMADO, EventoFixture.viaje(viaje, TipoViaje.REGULAR, null))))
            );
            verifyNoInteractions(proyeccionViajes);
        }
    }

    @Nested
    class ViajeEliminado {

        @Test
        void manejar_anulaLasComprasDelViaje() {
            // Arrange
            when(anulacionViaje.anularViaje(viaje.getId())).thenReturn(2);

            // Act
            new ManejadorViajeEliminado(anulacionViaje, validacion).manejar(EventoFixture.sobre(TipoEvento.VIAJE_ELIMINADO,
                    EventoFixture.viajeEliminado(viaje.getId()), 5L));

            // Assert
            verify(anulacionViaje).anularViaje(viaje.getId());
        }

        @Test
        void manejar_sinIdViaje_esNoRecuperable() {
            // Arrange
            SobreEvento<DatosViajeEliminado> evento = EventoFixture.sobre(TipoEvento.VIAJE_ELIMINADO,
                    EventoFixture.viajeEliminado(null), 5L);

            // Act & Assert
            assertThrows(EventoNoRecuperableException.class,
                    () -> new ManejadorViajeEliminado(anulacionViaje, validacion).manejar(evento));
            verifyNoInteractions(anulacionViaje);
        }
    }

    @Nested
    class PerfilCliente {

        @Test
        void manejar_proyectaElPerfil() {
            // Arrange
            DatosPerfilClienteActualizado datos = EventoFixture.perfil(UUID.randomUUID(), "1234567K");
            when(proyeccionClientes.aplicar(datos, 2L)).thenReturn(true);

            // Act
            new ManejadorPerfilClienteActualizado(proyeccionClientes, validacion)
                    .manejar(EventoFixture.sobre(TipoEvento.PERFIL_CLIENTE_ACTUALIZADO, datos, 2L));

            // Assert
            verify(proyeccionClientes).aplicar(datos, 2L);
        }

        @Test
        void manejar_sinIdUsuarioONombre_esNoRecuperable() {
            // Arrange
            ManejadorPerfilClienteActualizado manejador = new ManejadorPerfilClienteActualizado(proyeccionClientes, validacion);
            DatosPerfilClienteActualizado sinId = EventoFixture.perfil(null, "CF");
            DatosPerfilClienteActualizado sinNombre = new DatosPerfilClienteActualizado(UUID.randomUUID(), "CF", " ", true);

            // Act & Assert
            assertAll(
                    () -> assertThrows(EventoNoRecuperableException.class, () -> manejador.manejar(
                            EventoFixture.sobre(TipoEvento.PERFIL_CLIENTE_ACTUALIZADO, sinId, 1L))),
                    () -> assertThrows(EventoNoRecuperableException.class, () -> manejador.manejar(
                            EventoFixture.sobre(TipoEvento.PERFIL_CLIENTE_ACTUALIZADO, sinNombre, 1L)))
            );
            verifyNoInteractions(proyeccionClientes);
        }
    }
}
