package org.transportsgt.fleetservice.eventos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.payload.viajes.DatosLlegada;
import org.transportsgt.eventos.payload.viajes.DatosSalida;
import org.transportsgt.eventos.payload.identidad.DatosSucursal;
import org.transportsgt.eventos.payload.viajes.TipoViaje;
import org.transportsgt.fleetservice.services.proyeccion.impl.KilometrajeBusServiceImpl;
import org.transportsgt.fleetservice.services.proyeccion.impl.ProyeccionReferenciasServiceImpl;
import org.transportsgt.fleetservice.repositories.BusRepository;
import org.transportsgt.fleetservice.repositories.SucursalRefRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    private BusRepository busRepository;
    @Mock
    private SucursalRefRepository sucursalRefRepository;

    private static final UUID ID_BUS = UUID.fromString("b0000000-0000-4000-8000-000000000001");
    private static final UUID ID_VIAJE = UUID.randomUUID();

    private static DatosSucursal sucursal(UUID id, String nombre, boolean activa) {
        return new DatosSucursal(id, nombre, "Quetzaltenango", "1a. calle",
                new BigDecimal("14.83"), new BigDecimal("-91.51"), activa);
    }

    private static <T> SobreEvento<T> sobre(T datos, long version) {
        return new SobreEvento<>(UUID.randomUUID(), "tipo", 1, java.time.Instant.now(),
                "servicio-viajes", null, version, null, datos);
    }

    @Test
    void salidaPoneElBusEnTransito() {
        DatosSalida datos = new DatosSalida(ID_VIAJE, ID_BUS, UUID.randomUUID(),
                OffsetDateTime.now(ZoneOffset.UTC), new BigDecimal("120000.0"), UUID.randomUUID());
        when(busRepository.registrarSalida(ID_BUS, new BigDecimal("120000.0"))).thenReturn(1);

        new ManejadorSalidaRegistrada(new KilometrajeBusServiceImpl(busRepository)).manejar(sobre(datos, 1L));

        verify(busRepository).registrarSalida(ID_BUS, new BigDecimal("120000.0"));
    }

    @Test
    void salidaDeBusDesconocidoNoExplota() {
        DatosSalida datos = new DatosSalida(ID_VIAJE, ID_BUS, UUID.randomUUID(),
                OffsetDateTime.now(ZoneOffset.UTC), BigDecimal.TEN, null);
        when(busRepository.registrarSalida(eq(ID_BUS), any(BigDecimal.class))).thenReturn(0);

        new ManejadorSalidaRegistrada(new KilometrajeBusServiceImpl(busRepository)).manejar(sobre(datos, 1L));

        verify(busRepository).registrarSalida(ID_BUS, BigDecimal.TEN);
    }

    @Test
    void llegadaActualizaKilometraje() {
        DatosLlegada datos = new DatosLlegada(ID_VIAJE, TipoViaje.REGULAR, null, null,
                UUID.randomUUID(), ID_BUS, UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.TEN, OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC),
                BigDecimal.ONE, new BigDecimal("121000.0"), new BigDecimal("100.0"),
                new BigDecimal("50.0"), null);
        when(busRepository.registrarLlegada(ID_BUS, new BigDecimal("121000.0"))).thenReturn(1);

        new ManejadorLlegadaRegistrada(new KilometrajeBusServiceImpl(busRepository)).manejar(sobre(datos, 1L));

        verify(busRepository).registrarLlegada(ID_BUS, new BigDecimal("121000.0"));
    }

    @Test
    void llegadaDeBusDesconocidoNoExplota() {
        DatosLlegada datos = new DatosLlegada(ID_VIAJE, TipoViaje.REGULAR, null, null,
                UUID.randomUUID(), ID_BUS, UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.TEN, OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC),
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, null);
        when(busRepository.registrarLlegada(eq(ID_BUS), any(BigDecimal.class))).thenReturn(0);

        new ManejadorLlegadaRegistrada(new KilometrajeBusServiceImpl(busRepository)).manejar(sobre(datos, 1L));
    }

    @Test
    void sucursalCreadaHaceUpsertConLaVersionDelEvento() {
        UUID idSucursal = UUID.randomUUID();
        DatosSucursal datos = sucursal(idSucursal, "Xela", true);
        when(sucursalRefRepository.upsert(idSucursal, "Xela", true, 3L)).thenReturn(1);

        new ManejadorSucursalCreada(new ProyeccionReferenciasServiceImpl(sucursalRefRepository)).manejar(sobre(datos, 3L));

        verify(sucursalRefRepository).upsert(idSucursal, "Xela", true, 3L);
    }

    @Test
    void sucursalCreadaSinVersionUsaCero() {
        UUID idSucursal = UUID.randomUUID();
        DatosSucursal datos = sucursal(idSucursal, "Xela", true);
        SobreEvento<DatosSucursal> evento = new SobreEvento<>(UUID.randomUUID(), "SucursalCreada", 1,
                java.time.Instant.now(), "servicio-identidad", null, null, null, datos);
        when(sucursalRefRepository.upsert(idSucursal, "Xela", true, 0L)).thenReturn(0);

        new ManejadorSucursalCreada(new ProyeccionReferenciasServiceImpl(sucursalRefRepository)).manejar(evento);

        verify(sucursalRefRepository).upsert(idSucursal, "Xela", true, 0L);
    }

    @Test
    void sucursalActualizadaUsaElMismoUpsert() {
        UUID idSucursal = UUID.randomUUID();
        DatosSucursal datos = sucursal(idSucursal, "Xela Centro", false);
        when(sucursalRefRepository.upsert(idSucursal, "Xela Centro", false, 4L)).thenReturn(1);

        new ManejadorSucursalActualizada(new ProyeccionReferenciasServiceImpl(sucursalRefRepository)).manejar(sobre(datos, 4L));

        verify(sucursalRefRepository).upsert(idSucursal, "Xela Centro", false, 4L);
    }

    @Test
    void eventoViejoNoSeVuelveAEscribir() {
        UUID idSucursal = UUID.randomUUID();
        DatosSucursal datos = sucursal(idSucursal, "Xela", true);
        when(sucursalRefRepository.upsert(idSucursal, "Xela", true, 1L)).thenReturn(0);

        new ManejadorSucursalCreada(new ProyeccionReferenciasServiceImpl(sucursalRefRepository)).manejar(sobre(datos, 1L));

        verify(sucursalRefRepository).upsert(idSucursal, "Xela", true, 1L);
        verify(busRepository, never()).registrarSalida(any(), any());
    }

    @Test
    void versionDeEventoSeConvierteEnLong() {
        DatosSalida datos = new DatosSalida(ID_VIAJE, ID_BUS, UUID.randomUUID(),
                OffsetDateTime.now(ZoneOffset.UTC), BigDecimal.ONE, null);
        when(busRepository.registrarSalida(eq(ID_BUS), any(BigDecimal.class))).thenReturn(1);

        new ManejadorSalidaRegistrada(new KilometrajeBusServiceImpl(busRepository)).manejar(sobre(datos, 9L));

        ArgumentCaptor<BigDecimal> kilometraje = ArgumentCaptor.forClass(BigDecimal.class);
        verify(busRepository).registrarSalida(eq(ID_BUS), kilometraje.capture());
        assertEquals(BigDecimal.ONE, kilometraje.getValue());
    }
}
