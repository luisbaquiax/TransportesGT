package org.transportsgt.privaterentalservice.eventos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.payload.BusAsignado;
import org.transportsgt.eventos.payload.ChoferAsignado;
import org.transportsgt.eventos.payload.DatosLlegada;
import org.transportsgt.eventos.payload.DatosPerfilClienteActualizado;
import org.transportsgt.eventos.payload.DatosSucursal;
import org.transportsgt.eventos.payload.DatosViaje;
import org.transportsgt.eventos.payload.DatosViajeEliminado;
import org.transportsgt.eventos.payload.EstadoViaje;
import org.transportsgt.eventos.payload.TipoViaje;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;
import org.transportsgt.privaterentalservice.repositories.AlquilerRepository;
import org.transportsgt.privaterentalservice.repositories.ClienteRefRepository;
import org.transportsgt.privaterentalservice.repositories.SucursalRefRepository;
import org.transportsgt.privaterentalservice.services.comun.GuardadoAlquiler;
import org.transportsgt.privaterentalservice.services.comun.NormalizadorNit;
import org.transportsgt.privaterentalservice.services.comun.TransicionesAlquiler;
import org.transportsgt.privaterentalservice.services.proyeccion.impl.ProyeccionReferenciasServiceImpl;
import org.transportsgt.privaterentalservice.services.proyeccion.impl.SeguimientoViajeServiceImpl;
import org.transportsgt.privaterentalservice.soporte.DatosPrueba;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private ClienteRefRepository clienteRepository;
    @Mock
    private EventosAlquiler eventos;

    private final DatosPrueba datos = new DatosPrueba();
    private ProyeccionReferenciasServiceImpl proyeccion;
    private SeguimientoViajeServiceImpl seguimiento;

    /** Los manejadores delegan en los servicios; se prueba el recorrido completo hasta el repositorio. */
    @BeforeEach
    void setUp() {
        proyeccion = new ProyeccionReferenciasServiceImpl(sucursalRepository, clienteRepository, new NormalizadorNit());
        seguimiento = new SeguimientoViajeServiceImpl(alquilerRepository, new TransicionesAlquiler(),
                new GuardadoAlquiler(alquilerRepository), datos.calendario, eventos);
    }

    private <T> SobreEvento<T> sobre(UUID idAgregado, Long version, T datos) {
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
        new ManejadorSucursalCreada(proyeccion)
                .manejar(sobre(id, 0L, d));
        verify(sucursalRepository).upsert(id, "Quetzaltenango", true, 0L);
    }

    @Test
    void sucursalActualizadaHaceUpsert() {
        UUID id = UUID.randomUUID();
        DatosSucursal d = new DatosSucursal(id, "Xela", "Quetzaltenango",
                "Calle 1", null, null, false);
        new ManejadorSucursalActualizada(proyeccion)
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
        new ManejadorViajeProgramado(seguimiento).manejar(sobre(a.getId(), 3L, d));
        assertEquals(idViaje, a.getIdViaje());
        assertEquals(3L, a.getVersionViaje());
        verify(alquilerRepository).saveAndFlush(a);
    }

    @Test
    void viajeProgramadoSinAlquilerNoHaceNada() {
        UUID idAlquiler = UUID.randomUUID();
        when(alquilerRepository.findById(idAlquiler)).thenReturn(Optional.empty());
        DatosViaje d = new DatosViaje(UUID.randomUUID(), TipoViaje.REGULAR, EstadoViaje.PROGRAMADO,
                null, null, "X", "Y", idAlquiler, OffsetDateTime.now(), null, null, null, null);
        new ManejadorViajeProgramado(seguimiento).manejar(sobre(idAlquiler, 1L, d));
        verify(alquilerRepository, never()).saveAndFlush(any());
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
        new ManejadorViajeActualizado(seguimiento).manejar(sobre(a.getId(), 3L, d));
        assertEquals(idBus, a.getIdBus());
        assertEquals("P900ABC", a.getPlacaBus());
        assertEquals(idChofer, a.getIdChofer());
        assertEquals("Carlos Méndez", a.getNombreChofer());
        verify(alquilerRepository).saveAndFlush(a);
    }

    @Test
    void viajeEliminadoCancelaAlquilerConfirmado() {
        Alquiler a = alquilerConfirmado();
        a.setIdViaje(UUID.randomUUID());
        when(alquilerRepository.findById(a.getId())).thenReturn(Optional.of(a));
        DatosViajeEliminado d = new DatosViajeEliminado(a.getIdViaje(),
                TipoViaje.ALQUILER_PRIVADO, a.getId(), Instant.now(), UUID.randomUUID());
        when(alquilerRepository.saveAndFlush(a)).thenReturn(a);
        new ManejadorViajeEliminado(seguimiento).manejar(sobre(a.getId(), 3L, d));
        assertEquals(EstadoAlquiler.CANCELADO, a.getEstado());
        assertEquals("VIAJE_ELIMINADO", a.getMotivoCancelacion());
        verify(alquilerRepository).saveAndFlush(a);
        verify(eventos).alquilerCancelado(eq(a), eq("VIAJE_ELIMINADO"));
    }

    @Test
    void viajeEliminadoIgnoraFinalizado() {
        Alquiler a = alquilerConfirmado();
        a.setEstado(EstadoAlquiler.FINALIZADO);
        when(alquilerRepository.findById(a.getId())).thenReturn(Optional.of(a));
        DatosViajeEliminado d = new DatosViajeEliminado(UUID.randomUUID(),
                TipoViaje.ALQUILER_PRIVADO, a.getId(), Instant.now(), UUID.randomUUID());
        new ManejadorViajeEliminado(seguimiento).manejar(sobre(a.getId(), 3L, d));
        verify(alquilerRepository, never()).saveAndFlush(any());
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
        new ManejadorLlegadaRegistrada(seguimiento).manejar(sobre(a.getId(), 3L, d));
        assertEquals(EstadoAlquiler.FINALIZADO, a.getEstado());
        verify(alquilerRepository).saveAndFlush(a);
    }

    @Test
    void llegadaIgnoraAlquilerNoConfirmado() {
        Alquiler a = alquilerConfirmado();
        a.setEstado(EstadoAlquiler.COTIZADO);
        when(alquilerRepository.findById(a.getId())).thenReturn(Optional.of(a));
        DatosLlegada d = new DatosLlegada(UUID.randomUUID(), TipoViaje.ALQUILER_PRIVADO, a.getId(),
                null, null, null, null, null, null, null, null, null, null, null, null, null);
        new ManejadorLlegadaRegistrada(seguimiento).manejar(sobre(a.getId(), 1L, d));
        verify(alquilerRepository, never()).saveAndFlush(any());
    }

    @Test
    void viajeSinIdAlquilerSeIgnora() {
        DatosViaje d = new DatosViaje(UUID.randomUUID(), TipoViaje.REGULAR, EstadoViaje.PROGRAMADO,
                null, null, "X", "Y", null, OffsetDateTime.now(), null, null, null, null);
        new ManejadorViajeProgramado(seguimiento).manejar(sobre(UUID.randomUUID(), 1L, d));
        verify(alquilerRepository, never()).findById(any());
    }

    @Test
    void sucursalConVersionNulaUsaCero() {
        UUID id = UUID.randomUUID();
        DatosSucursal d = new DatosSucursal(id, "X", null, null, null, null, true);
        new ManejadorSucursalCreada(proyeccion).manejar(sobre(id, null, d));
        verify(sucursalRepository).upsert(id, "X", true, 0L);
    }

    @Test
    void perfilClienteActualizadoGuardaElNitNormalizado() {
        UUID id = UUID.randomUUID();
        DatosPerfilClienteActualizado d = new DatosPerfilClienteActualizado(id, "1234567-k", "Ana López", true);
        new ManejadorPerfilClienteActualizado(proyeccion).manejar(sobre(id, 4L, d));
        verify(clienteRepository).upsert(id, "1234567K", "Ana López", true, 4L);
    }

    @Test
    void perfilSinNitGuardaNull() {
        UUID id = UUID.randomUUID();
        DatosPerfilClienteActualizado d = new DatosPerfilClienteActualizado(id, null, "Ana López", true);
        new ManejadorPerfilClienteActualizado(proyeccion).manejar(sobre(id, 1L, d));
        verify(clienteRepository).upsert(id, null, "Ana López", true, 1L);
    }

    @Test
    void viajeActualizadoAtrasadoNoPisaElBusMasReciente() {
        Alquiler a = alquilerConfirmado();
        a.setIdBus(UUID.randomUUID());
        a.setPlacaBus("P111AAA");
        a.setVersionViaje(5L);
        when(alquilerRepository.findById(a.getId())).thenReturn(Optional.of(a));
        DatosViaje d = new DatosViaje(UUID.randomUUID(), TipoViaje.ALQUILER_PRIVADO, EstadoViaje.PROGRAMADO,
                null, null, "X", "Y", a.getId(), OffsetDateTime.now(), null, null,
                new BusAsignado(UUID.randomUUID(), "P222BBB", 45), null);

        new ManejadorViajeActualizado(seguimiento).manejar(sobre(a.getId(), 4L, d));

        assertEquals("P111AAA", a.getPlacaBus());
        assertEquals(5L, a.getVersionViaje());
        verify(alquilerRepository, never()).saveAndFlush(any());
    }

    @Test
    void viajeEliminadoDeCotizadoTambienCancela() {
        Alquiler a = alquilerConfirmado();
        a.setEstado(EstadoAlquiler.COTIZADO);
        when(alquilerRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(alquilerRepository.saveAndFlush(a)).thenReturn(a);
        DatosViajeEliminado d = new DatosViajeEliminado(UUID.randomUUID(),
                TipoViaje.ALQUILER_PRIVADO, a.getId(), Instant.now(), UUID.randomUUID());
        new ManejadorViajeEliminado(seguimiento).manejar(sobre(a.getId(), 3L, d));
        assertEquals(EstadoAlquiler.CANCELADO, a.getEstado());
        assertEquals(datos.calendario.ahora(), a.getCanceladoEn());
    }
}
