package org.transportsgt.tripservice.eventos;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.mensajeria.publicacion.RegistroOutbox;
import org.transportsgt.eventos.payload.flota.BusAsignado;
import org.transportsgt.eventos.payload.flota.ChoferAsignado;
import org.transportsgt.eventos.payload.viajes.DatosLlegada;
import org.transportsgt.eventos.payload.viajes.DatosRuta;
import org.transportsgt.eventos.payload.viajes.DatosRutaEliminada;
import org.transportsgt.eventos.payload.viajes.DatosSalida;
import org.transportsgt.eventos.payload.viajes.DatosViaje;
import org.transportsgt.eventos.payload.viajes.DatosViajeEliminado;
import org.transportsgt.eventos.payload.viajes.EstadoViaje;
import org.transportsgt.eventos.payload.viajes.TipoViaje;
import org.transportsgt.tripservice.models.Ruta;
import org.transportsgt.tripservice.models.Viaje;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventosViajeTest {

    private static final OffsetDateTime SALIDA = OffsetDateTime.parse("2026-10-20T08:00:00-06:00");

    @Mock
    private RegistroOutbox outbox;

    private EventosViaje eventos() {
        return new EventosViaje(outbox, new MapeadorEventosViaje());
    }

    private static Ruta ruta() {
        return Ruta.builder().id(UUID.randomUUID()).idSucursalOrigen(UUID.randomUUID())
                .idSucursalDestino(UUID.randomUUID()).distanciaKm(new BigDecimal("120.5"))
                .precioBoleto(new BigDecimal("75.00")).version(2L).build();
    }

    private static Viaje viaje(Ruta ruta) {
        return Viaje.builder().id(UUID.randomUUID()).tipoViaje(TipoViaje.REGULAR).estado(EstadoViaje.PROGRAMADO)
                .ruta(ruta).idSucursalOrigen(UUID.randomUUID()).nombreOrigen("Xela").nombreDestino("Guatemala")
                .fechaHoraSalida(SALIDA).fechaHoraLlegadaEstimada(SALIDA.plusHours(4))
                .precioBoleto(new BigDecimal("75.00")).idBus(UUID.randomUUID()).idChofer(UUID.randomUUID())
                .version(5L).build();
    }

    private <T> T capturar(TipoEvento tipo, UUID idAgregado, Long version, Class<T> clase) {
        ArgumentCaptor<T> captor = ArgumentCaptor.forClass(clase);
        if (version == null) {
            verify(outbox).registrar(eq(tipo), eq(idAgregado), isNull(), captor.capture());
        } else {
            verify(outbox).registrar(eq(tipo), eq(idAgregado), eq(version), captor.capture());
        }
        return captor.getValue();
    }

    @Nested
    class Rutas {

        @Test
        void rutaCreadaYActualizada_publicanLosDatosDeLaRuta() {
            // Arrange
            Ruta ruta = ruta();

            // Act
            eventos().rutaCreada(ruta);
            eventos().rutaActualizada(ruta);

            // Assert
            DatosRuta creada = capturar(TipoEvento.RUTA_CREADA, ruta.getId(), 2L, DatosRuta.class);
            assertEquals(new DatosRuta(ruta.getId(), ruta.getIdSucursalOrigen(), ruta.getIdSucursalDestino(),
                    ruta.getDistanciaKm(), ruta.getPrecioBoleto()), creada);
            assertEquals(creada, capturar(TipoEvento.RUTA_ACTUALIZADA, ruta.getId(), 2L, DatosRuta.class));
        }

        @Test
        void rutaEliminada_sinVersion() {
            // Arrange
            UUID idRuta = UUID.randomUUID();

            // Act
            eventos().rutaEliminada(idRuta);

            // Assert
            assertEquals(idRuta, capturar(TipoEvento.RUTA_ELIMINADA, idRuta, null, DatosRutaEliminada.class).idRuta());
        }
    }

    @Nested
    class Viajes {

        @Test
        void viajeProgramado_incluyeBusYChofer() {
            // Arrange
            Viaje viaje = viaje(ruta());
            BusAsignado bus = new BusAsignado(viaje.getIdBus(), "P123ABC", 40);
            ChoferAsignado chofer = new ChoferAsignado(viaje.getIdChofer(), "Ana López");

            // Act
            eventos().viajeProgramado(viaje, bus, chofer);

            // Assert
            DatosViaje datos = capturar(TipoEvento.VIAJE_PROGRAMADO, viaje.getId(), 5L, DatosViaje.class);
            assertEquals(viaje.getRuta().getId(), datos.idRuta());
            assertEquals(bus, datos.bus());
            assertEquals(chofer, datos.chofer());
            assertEquals("Guatemala", datos.nombreDestino());
        }

        @Test
        void viajeActualizadoSinRuta_publicaRutaNula() {
            // Arrange
            Viaje viaje = viaje(null);

            // Act
            eventos().viajeActualizado(viaje, null, null);

            // Assert
            DatosViaje datos = capturar(TipoEvento.VIAJE_ACTUALIZADO, viaje.getId(), 5L, DatosViaje.class);
            assertNull(datos.idRuta());
            assertNull(datos.bus());
        }

        @Test
        void viajeEliminado_registraQuienLoElimino() {
            // Arrange
            Viaje viaje = viaje(ruta());
            UUID idUsuario = UUID.randomUUID();

            // Act
            eventos().viajeEliminado(viaje, idUsuario);

            // Assert
            DatosViajeEliminado datos = capturar(TipoEvento.VIAJE_ELIMINADO, viaje.getId(), 5L,
                    DatosViajeEliminado.class);
            assertEquals(idUsuario, datos.idUsuarioEliminador());
            assertEquals(TipoViaje.REGULAR, datos.tipoViaje());
        }

        @Test
        void salidaRegistrada_llevaBusChoferYKilometraje() {
            // Arrange
            Viaje viaje = viaje(ruta());
            UUID idUsuario = UUID.randomUUID();

            // Act
            eventos().salidaRegistrada(viaje, SALIDA, new BigDecimal("1000"), idUsuario);

            // Assert
            assertEquals(new DatosSalida(viaje.getId(), viaje.getIdBus(), viaje.getIdChofer(), SALIDA,
                            new BigDecimal("1000"), idUsuario),
                    capturar(TipoEvento.SALIDA_REGISTRADA, viaje.getId(), 5L, DatosSalida.class));
        }

        @Test
        void llegadaRegistrada_conYSinRuta() {
            // Arrange
            Viaje regular = viaje(ruta());
            Viaje alquiler = viaje(null);
            UUID sucursalBus = UUID.randomUUID();

            // Act
            eventos().llegadaRegistrada(regular, sucursalBus, new BigDecimal("300"), SALIDA, SALIDA.plusHours(4),
                    new BigDecimal("1000"), new BigDecimal("1120"), new BigDecimal("120"), new BigDecimal("450"),
                    UUID.randomUUID());
            eventos().llegadaRegistrada(alquiler, sucursalBus, new BigDecimal("300"), SALIDA, SALIDA.plusHours(4),
                    new BigDecimal("1000"), new BigDecimal("1120"), new BigDecimal("120"), new BigDecimal("450"),
                    UUID.randomUUID());

            // Assert
            DatosLlegada conRuta = capturar(TipoEvento.LLEGADA_REGISTRADA, regular.getId(), 5L, DatosLlegada.class);
            assertEquals(regular.getRuta().getId(), conRuta.idRuta());
            assertEquals(sucursalBus, conRuta.idSucursalBus());
            assertNull(capturar(TipoEvento.LLEGADA_REGISTRADA, alquiler.getId(), 5L, DatosLlegada.class).idRuta());
        }
    }
}
