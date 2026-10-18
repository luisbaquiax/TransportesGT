package org.transportsgt.privaterentalservice.eventos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.payload.BusAsignado;
import org.transportsgt.eventos.payload.ChoferAsignado;
import org.transportsgt.eventos.payload.DatosLlegada;
import org.transportsgt.eventos.payload.DatosSucursal;
import org.transportsgt.eventos.payload.DatosViaje;
import org.transportsgt.eventos.payload.DatosViajeEliminado;
import org.transportsgt.eventos.payload.EstadoViaje;
import org.transportsgt.eventos.payload.TipoViaje;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;
import org.transportsgt.privaterentalservice.repositories.AlquilerRepository;
import org.transportsgt.privaterentalservice.repositories.SucursalRefRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManejadoresEventosTest {

    @Mock
    private AlquilerRepository alquilerRepository;
    @Mock
    private SucursalRefRepository sucursalRepository;
    @Mock
    private EventosAlquiler eventos;

    private <T> SobreEvento<T> sobre(UUID idAgregado, long version, T datos) {
        return new SobreEvento<>(UUID.randomUUID(), "prueba", 1, Instant.now(),
                "pruebas", idAgregado, version, null, datos);
    }

    private Alquiler alquilerConfirmado() {
        return Alquiler.builder()
                .id(UUID.randomUUID())
                .estado(EstadoAlquiler.CONFIRMADO)
                .idUsuarioCliente(UUID.randomUUID())
                .nombreCliente("Cliente")
                .nitCliente("1234567-8")
                .idSucursalOrigen(UUID.randomUUID())
                .origen("X")
                .destino("Y")
                .fechaHoraSalida(OffsetDateTime.now().plusDays(5))
                .idaYVuelta(false)
                .numeroPasajeros((short) 40)
                .distanciaEstimadaKm(new BigDecimal("100"))
                .precioEstimado(new BigDecimal("1500"))
                .precioFinal(new BigDecimal("1600"))
                .fechaHoraLlegadaEstimada(OffsetDateTime.now().plusDays(5).plusHours(4))
                .version(2L)
                .build();
    }

    @Test
    void sucursalCreadaHaceUpsert() {
        UUID id = UUID.randomUUID();
        DatosSucursal d = new DatosSucursal(id, "Quetzaltenango", "Quetzaltenango",
                "Calle 1", null, null, true);
        new ManejadorSucursalCreada(sucursalRepository)
                .manejar(sobre(id, 0L, d));
        verify(sucursalRepository).upsert(id, "Quetzaltenango", true, 0L);
    }

    @Test
    void sucursalActualizadaHaceUpsert() {
        UUID id = UUID.randomUUID();
        DatosSucursal d = new DatosSucursal(id, "Xela", "Quetzaltenango",
                "Calle 1", null, null, false);
        new ManejadorSucursalActualizada(sucursalRepository)
                .manejar(sobre(id, 3L, d));
        verify(sucursalRepository).upsert(id, "Xela", false, 3L);
    }

    @Test
    void viajeProgramadoEnlazaAlquiler() {
        Alquiler a = alquilerConfirmado();
        UUID idViaje = UUID.randomUUID();
        when(alquilerRepository.findById(a.getId())).thenReturn(Optional.of(a));
        DatosViaje d = new DatosViaje(idViaje, TipoViaje.ALQUILER_PRIVADO, EstadoViaje.PROGRAMADO,
                null, null, "X", "Y", a.getId(), OffsetDateTime.now(), null, null, null, null);
        new ManejadorViajeProgramado(alquilerRepository).manejar(sobre(a.getId(), 3L, d));
        assertEquals(idViaje, a.getIdViaje());
        verify(alquilerRepository).save(a);
    }

    @Test
    void viajeProgramadoSinAlquilerNoHaceNada() {
        UUID idAlquiler = UUID.randomUUID();
        when(alquilerRepository.findById(idAlquiler)).thenReturn(Optional.empty());
        DatosViaje d = new DatosViaje(UUID.randomUUID(), TipoViaje.REGULAR, EstadoViaje.PROGRAMADO,
                null, null, "X", "Y", idAlquiler, OffsetDateTime.now(), null, null, null, null);
        new ManejadorViajeProgramado(alquilerRepository).manejar(sobre(idAlquiler, 1L, d));
        verify(alquilerRepository, never()).save(any());
    }

    @Test
    void viajeActualizadoReflejaBusYChofer() {
        Alquiler a = alquilerConfirmado();
        UUID idViaje = UUID.randomUUID();
        UUID idBus = UUID.randomUUID();
        UUID idChofer = UUID.randomUUID();
        when(alquilerRepository.findById(a.getId())).thenReturn(Optional.of(a));
        DatosViaje d = new DatosViaje(idViaje, TipoViaje.ALQUILER_PRIVADO, EstadoViaje.PROGRAMADO,
                null, null, "X", "Y", a.getId(), OffsetDateTime.now(), null, null,
                new BusAsignado(idBus, "P900ABC", 45),
                new ChoferAsignado(idChofer, "Carlos Méndez"));
        new ManejadorViajeActualizado(alquilerRepository).manejar(sobre(a.getId(), 3L, d));
        assertEquals(idBus, a.getIdBus());
        assertEquals("P900ABC", a.getPlacaBus());
        assertEquals(idChofer, a.getIdChofer());
        assertEquals("Carlos Méndez", a.getNombreChofer());
        verify(alquilerRepository).save(a);
    }

    @Test
    void viajeEliminadoCancelaAlquilerConfirmado() {
        Alquiler a = alquilerConfirmado();
        a.setIdViaje(UUID.randomUUID());
        when(alquilerRepository.findById(a.getId())).thenReturn(Optional.of(a));
        DatosViajeEliminado d = new DatosViajeEliminado(a.getIdViaje(),
                TipoViaje.ALQUILER_PRIVADO, a.getId(), Instant.now(), UUID.randomUUID());
        new ManejadorViajeEliminado(alquilerRepository, eventos).manejar(sobre(a.getId(), 3L, d));
        assertEquals(EstadoAlquiler.CANCELADO, a.getEstado());
        assertEquals("VIAJE_ELIMINADO", a.getMotivoCancelacion());
        verify(alquilerRepository).save(a);
        verify(eventos).alquilerCancelado(eq(a), eq("VIAJE_ELIMINADO"));
    }

    @Test
    void viajeEliminadoIgnoraFinalizado() {
        Alquiler a = alquilerConfirmado();
        a.setEstado(EstadoAlquiler.FINALIZADO);
        when(alquilerRepository.findById(a.getId())).thenReturn(Optional.of(a));
        DatosViajeEliminado d = new DatosViajeEliminado(UUID.randomUUID(),
                TipoViaje.ALQUILER_PRIVADO, a.getId(), Instant.now(), UUID.randomUUID());
        new ManejadorViajeEliminado(alquilerRepository, eventos).manejar(sobre(a.getId(), 3L, d));
        verify(alquilerRepository, never()).save(any());
        verify(eventos, never()).alquilerCancelado(any(), any());
    }

    @Test
    void llegadaFinalizaAlquiler() {
        Alquiler a = alquilerConfirmado();
        a.setIdViaje(UUID.randomUUID());
        when(alquilerRepository.findById(a.getId())).thenReturn(Optional.of(a));
        DatosLlegada d = new DatosLlegada(a.getIdViaje(), TipoViaje.ALQUILER_PRIVADO, a.getId(),
                null, null, UUID.randomUUID(), null, UUID.randomUUID(), null,
                OffsetDateTime.now().minusHours(6), OffsetDateTime.now(),
                null, null, null, null, null);
        new ManejadorLlegadaRegistrada(alquilerRepository).manejar(sobre(a.getId(), 3L, d));
        assertEquals(EstadoAlquiler.FINALIZADO, a.getEstado());
        verify(alquilerRepository).save(a);
    }

    @Test
    void llegadaIgnoraAlquilerNoConfirmado() {
        Alquiler a = alquilerConfirmado();
        a.setEstado(EstadoAlquiler.COTIZADO);
        when(alquilerRepository.findById(a.getId())).thenReturn(Optional.of(a));
        DatosLlegada d = new DatosLlegada(UUID.randomUUID(), TipoViaje.ALQUILER_PRIVADO, a.getId(),
                null, null, null, null, null, null, null, null, null, null, null, null, null);
        new ManejadorLlegadaRegistrada(alquilerRepository).manejar(sobre(a.getId(), 1L, d));
        verify(alquilerRepository, never()).save(any());
    }

    @Test
    void viajeSinIdAlquilerSeIgnora() {
        DatosViaje d = new DatosViaje(UUID.randomUUID(), TipoViaje.REGULAR, EstadoViaje.PROGRAMADO,
                null, null, "X", "Y", null, OffsetDateTime.now(), null, null, null, null);
        new ManejadorViajeProgramado(alquilerRepository).manejar(sobre(UUID.randomUUID(), 1L, d));
        verify(alquilerRepository, never()).findById(any());
    }
}
