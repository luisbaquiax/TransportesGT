package org.transportsgt.reportingservice.services.proyecciones.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.EventoNoRecuperableException;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosLlegada;
import org.transportsgt.eventos.payload.DatosSalida;
import org.transportsgt.eventos.payload.DatosViaje;
import org.transportsgt.eventos.payload.EstadoViaje;
import org.transportsgt.reportingservice.exception.ProyeccionNoDisponibleException;
import org.transportsgt.reportingservice.fixture.EventoFixture;
import org.transportsgt.reportingservice.fixture.RepSucursalFixture;
import org.transportsgt.reportingservice.repositories.RepBusRepository;
import org.transportsgt.reportingservice.repositories.RepRutaRepository;
import org.transportsgt.reportingservice.repositories.RepViajeRepository;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProyeccionViajesServiceImplTest {

    @Mock
    private RepRutaRepository rutaRepository;
    @Mock
    private RepViajeRepository viajeRepository;
    @Mock
    private RepBusRepository busRepository;

    private ProyeccionViajesServiceImpl servicio;
    private UUID idRuta;
    private UUID idViaje;
    private UUID idBus;
    private UUID idChofer;

    @BeforeEach
    void setUp() {
        servicio = new ProyeccionViajesServiceImpl(new CamposObligatorios(), rutaRepository, viajeRepository,
                busRepository, new VersionAgregado());
        idRuta = UUID.randomUUID();
        idViaje = UUID.randomUUID();
        idBus = UUID.randomUUID();
        idChofer = UUID.randomUUID();
    }

    @Nested
    class Rutas {

        @Test
        void aplicarRuta_cuandoLlega_haceUpsertPorVersion() {
            // Arrange
            when(rutaRepository.upsert(any(), any(), any(), any(), any(), anyLong())).thenReturn(1);

            // Act
            servicio.aplicarRuta(EventoFixture.sobre(TipoEvento.RUTA_CREADA, EventoFixture.ruta(idRuta,
                    RepSucursalFixture.QUETZALTENANGO, RepSucursalFixture.GUATEMALA), 1L));

            // Assert
            verify(rutaRepository).upsert(idRuta, RepSucursalFixture.QUETZALTENANGO, RepSucursalFixture.GUATEMALA,
                    new BigDecimal("205.50"), new BigDecimal("85.00"), 1L);
        }

        @Test
        void aplicarRuta_cuandoVersionVieja_noFalla() {
            // Arrange
            when(rutaRepository.upsert(any(), any(), any(), any(), any(), anyLong())).thenReturn(0);

            // Act
            servicio.aplicarRuta(EventoFixture.sobre(TipoEvento.RUTA_ACTUALIZADA, EventoFixture.ruta(idRuta,
                    RepSucursalFixture.QUETZALTENANGO, RepSucursalFixture.GUATEMALA), 1L));

            // Assert
            verify(rutaRepository).upsert(eq(idRuta), any(), any(), any(), any(), eq(1L));
        }

        @Test
        void eliminarRuta_cuandoExiste_laMarcaEliminada() {
            // Arrange
            when(rutaRepository.marcarEliminada(idRuta)).thenReturn(1);

            // Act
            servicio.eliminarRuta(EventoFixture.sobre(TipoEvento.RUTA_ELIMINADA, EventoFixture.rutaEliminada(idRuta)));

            // Assert
            verify(rutaRepository).marcarEliminada(idRuta);
        }

        @Test
        void eliminarRuta_cuandoAunNoExiste_esTransitorio() {
            // Arrange
            when(rutaRepository.marcarEliminada(idRuta)).thenReturn(0);

            // Act & Assert
            assertThrows(ProyeccionNoDisponibleException.class, () -> servicio.eliminarRuta(
                    EventoFixture.sobre(TipoEvento.RUTA_ELIMINADA, EventoFixture.rutaEliminada(idRuta))));
        }
    }

    @Nested
    class Viajes {

        @Test
        void aplicarViaje_cuandoTraeBusYChofer_losProyecta() {
            // Arrange
            when(viajeRepository.upsert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), anyLong())).thenReturn(1);

            // Act
            servicio.aplicarViaje(EventoFixture.sobre(TipoEvento.VIAJE_PROGRAMADO, EventoFixture.viaje(idViaje, idRuta,
                    RepSucursalFixture.QUETZALTENANGO, idBus, idChofer, EstadoViaje.PROGRAMADO), 1L));

            // Assert
            verify(viajeRepository).upsert(idViaje, "REGULAR", "PROGRAMADO", idRuta, null,
                    RepSucursalFixture.QUETZALTENANGO, "Quetzaltenango", "Ciudad de Guatemala", idBus, idChofer,
                    EventoFixture.SALIDA, EventoFixture.LLEGADA, 1L);
        }

        @Test
        void aplicarViaje_cuandoPendienteAsignacion_pasaBusYChoferNulos() {
            // Arrange
            when(viajeRepository.upsert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), anyLong())).thenReturn(0);

            // Act
            servicio.aplicarViaje(EventoFixture.sobre(TipoEvento.VIAJE_ACTUALIZADO, EventoFixture.viaje(idViaje, idRuta,
                    RepSucursalFixture.QUETZALTENANGO, null, null, EstadoViaje.PENDIENTE_ASIGNACION), 2L));

            // Assert
            verify(viajeRepository).upsert(eq(idViaje), any(), eq("PENDIENTE_ASIGNACION"), any(), any(), any(), any(),
                    any(), isNull(), isNull(), any(), any(), eq(2L));
        }

        @Test
        void aplicarViaje_cuandoNoTraeEstado_esNoRecuperable() {
            // Arrange
            DatosViaje sinEstado = EventoFixture.viaje(idViaje, idRuta, RepSucursalFixture.QUETZALTENANGO, null, null,
                    null);

            // Act & Assert
            assertThrows(EventoNoRecuperableException.class, () -> servicio.aplicarViaje(
                    EventoFixture.sobre(TipoEvento.VIAJE_PROGRAMADO, sinEstado, 1L)));
        }

        @Test
        void eliminarViaje_cuandoExiste_loMarcaYCuandoNo_esTransitorio() {
            // Arrange
            UUID otro = UUID.randomUUID();
            when(viajeRepository.marcarEliminado(idViaje)).thenReturn(1);
            when(viajeRepository.marcarEliminado(otro)).thenReturn(0);

            // Act
            servicio.eliminarViaje(EventoFixture.sobre(TipoEvento.VIAJE_ELIMINADO, EventoFixture.viajeEliminado(idViaje)));

            // Assert
            verify(viajeRepository).marcarEliminado(idViaje);
            assertThrows(ProyeccionNoDisponibleException.class, () -> servicio.eliminarViaje(
                    EventoFixture.sobre(TipoEvento.VIAJE_ELIMINADO, EventoFixture.viajeEliminado(otro))));
        }
    }

    @Nested
    class RegistrarSalida {

        @Test
        void registrarSalida_cuandoViajeExiste_actualizaViajeYBusEnTransito() {
            // Arrange
            DatosSalida salida = EventoFixture.salida(idViaje, idBus, "1000.0");
            when(viajeRepository.existsById(idViaje)).thenReturn(true);
            when(viajeRepository.registrarSalida(idViaje, salida.fechaHoraSalidaReal(), salida.kilometrajeInicial()))
                    .thenReturn(1);

            // Act
            servicio.registrarSalida(EventoFixture.sobre(TipoEvento.SALIDA_REGISTRADA, salida));

            // Assert
            verify(busRepository).registrarSalida(idBus);
        }

        @Test
        void registrarSalida_cuandoLaLlegadaYaSeAplico_noVuelveElBusAEnTransito() {
            // Arrange
            DatosSalida salida = EventoFixture.salida(idViaje, idBus, "1000.0");
            when(viajeRepository.existsById(idViaje)).thenReturn(true);
            when(viajeRepository.registrarSalida(any(), any(), any())).thenReturn(0);

            // Act
            servicio.registrarSalida(EventoFixture.sobre(TipoEvento.SALIDA_REGISTRADA, salida));

            // Assert
            verify(busRepository, never()).registrarSalida(any());
        }

        @Test
        void registrarSalida_cuandoViajeNoExiste_esTransitorio() {
            // Arrange
            when(viajeRepository.existsById(idViaje)).thenReturn(false);

            // Act & Assert
            assertThrows(ProyeccionNoDisponibleException.class, () -> servicio.registrarSalida(
                    EventoFixture.sobre(TipoEvento.SALIDA_REGISTRADA, EventoFixture.salida(idViaje, idBus, "1.0"))));
        }
    }

    @Nested
    class RegistrarLlegada {

        @Test
        void registrarLlegada_cuandoViajeExiste_finalizaYActualizaKilometraje() {
            // Arrange
            DatosLlegada llegada = EventoFixture.llegada(idViaje, idBus, "1000.0", "1205.5");
            when(viajeRepository.existsById(idViaje)).thenReturn(true);
            when(busRepository.registrarLlegada(idBus, llegada.kilometrajeFinal())).thenReturn(1);

            // Act
            servicio.registrarLlegada(EventoFixture.sobre(TipoEvento.LLEGADA_REGISTRADA, llegada));

            // Assert
            verify(viajeRepository).registrarLlegada(idViaje, llegada.fechaHoraSalidaReal(),
                    llegada.fechaHoraLlegadaReal(), new BigDecimal("1000.0"), new BigDecimal("1205.5"),
                    new BigDecimal("205.5"));
        }

        @Test
        void registrarLlegada_cuandoBusAunNoEstaProyectado_soloAvisa() {
            // Arrange
            DatosLlegada llegada = EventoFixture.llegada(idViaje, idBus, "1000.0", "1205.5");
            when(viajeRepository.existsById(idViaje)).thenReturn(true);
            when(busRepository.registrarLlegada(any(), any())).thenReturn(0);

            // Act
            servicio.registrarLlegada(EventoFixture.sobre(TipoEvento.LLEGADA_REGISTRADA, llegada));

            // Assert
            verify(busRepository).registrarLlegada(idBus, new BigDecimal("1205.5"));
        }

        @Test
        void registrarLlegada_cuandoViajeNoExiste_esTransitorio() {
            // Arrange
            when(viajeRepository.existsById(idViaje)).thenReturn(false);

            // Act & Assert
            assertThrows(ProyeccionNoDisponibleException.class, () -> servicio.registrarLlegada(EventoFixture.sobre(
                    TipoEvento.LLEGADA_REGISTRADA, EventoFixture.llegada(idViaje, idBus, "1.0", "2.0"))));
        }
    }
}
