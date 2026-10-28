package org.transportsgt.fleetservice.services.bus.impl;

import org.transportsgt.fleetservice.repositories.ChoferRepository;
import org.transportsgt.fleetservice.services.comun.BuscadorFlota;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.transportsgt.eventos.payload.flota.EstadoOperativo;
import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.fleetservice.clients.ConsultaViajes;
import org.transportsgt.fleetservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.fleetservice.dto.request.ActualizarBusRequest;
import org.transportsgt.fleetservice.dto.request.CambiarActivoRequest;
import org.transportsgt.fleetservice.dto.request.CambiarEstadoOperativoRequest;
import org.transportsgt.fleetservice.dto.request.CrearBusRequest;
import org.transportsgt.fleetservice.dto.response.BusResponse;
import org.transportsgt.fleetservice.dto.response.PaginaResponse;
import org.transportsgt.fleetservice.eventos.EventosFlota;
import org.transportsgt.fleetservice.exception.BusConViajesException;
import org.transportsgt.fleetservice.exception.BusNoEncontradoException;
import org.transportsgt.fleetservice.exception.EstadoOperativoInvalidoException;
import org.transportsgt.fleetservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.fleetservice.exception.PlacaDuplicadaException;
import org.transportsgt.fleetservice.exception.RegistroModificadoException;
import org.transportsgt.fleetservice.exception.ViajesNoDisponibleException;
import org.transportsgt.fleetservice.mappers.BusMapper;
import org.transportsgt.fleetservice.models.Bus;
import org.transportsgt.fleetservice.repositories.BusRepository;
import org.transportsgt.fleetservice.repositories.SucursalRefRepository;
import org.transportsgt.fleetservice.services.autorizacion.AutorizacionSucursal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GestionBusesServiceImplTest {

    @Mock
    private BusRepository busRepository;
    @Mock
    private SucursalRefRepository sucursalRefRepository;
    @Mock
    private ConsultaViajes consultaViajes;
    @Mock
    private BusMapper busMapper;
    @Mock
    private EventosFlota eventos;

    private GestionBusesServiceImpl servicio;

    private final UUID idSucursal = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private final UUID idBus = UUID.fromString("b0000000-0000-4000-8000-000000000001");
    private final UsuarioAutenticado adminSucursal =
            new UsuarioAutenticado(UUID.randomUUID(), "admin.xela@transportes.gt",
                    Rol.ADMIN_SUCURSAL, idSucursal, "Admin Xela");
    private final UsuarioAutenticado adminSistema =
            new UsuarioAutenticado(UUID.randomUUID(), "admin@transportes.gt",
                    Rol.ADMIN_SISTEMA, null, "Admin Sistema");

    @Mock
    private ChoferRepository choferRepository;

    @BeforeEach
    void setUp() {
        servicio = new GestionBusesServiceImpl(busRepository, new BuscadorFlota(busRepository, choferRepository),
                new AutorizacionSucursal(sucursalRefRepository), new ValidadorBus(busRepository),
                consultaViajes, busMapper, eventos);
    }

    private Bus bus(long version) {
        return Bus.builder()
                .id(idBus)
                .idSucursal(idSucursal)
                .placa("P900ABC")
                .marca("Toyota")
                .modelo("Hiace")
                .anioFabricacion((short) 2019)
                .capacidadPasajeros((short) 45)
                .estadoOperativo(EstadoOperativo.DISPONIBLE)
                .kilometrajeActual(new BigDecimal("1000.0"))
                .activo(true)
                .version(version)
                .build();
    }

    private CrearBusRequest requestCrear() {
        return new CrearBusRequest(" P900ABC ", "Toyota", "Hiace", (short) 2019, (short) 45, null);
    }

    /** Respuesta vacía para no depender del MapStruct en las unitarias. */
    private static BusResponse respuestaBus() {
        return new BusResponse(null, null, null, null, null, null, null, null, null, null, true, null, null, null);
    }

    private void sucursalOk() {
        when(sucursalRefRepository.existsById(idSucursal)).thenReturn(true);
        when(sucursalRefRepository.existsByIdAndActivaTrue(idSucursal)).thenReturn(true);
    }

    private static void assertNotNull(Object valor) {
        org.junit.jupiter.api.Assertions.assertNotNull(valor);
    }

    @Test
    void crearGuardaConEstadoDisponibleYPublicaEvento() {
        sucursalOk();
        when(busRepository.existsByPlacaIgnoreCase("P900ABC")).thenReturn(false);
        when(busRepository.saveAndFlush(any(Bus.class))).thenAnswer(inv -> {
            Bus guardado = inv.getArgument(0);
            guardado.setVersion(0L);
            return guardado;
        });
        BusResponse respuesta = respuestaBus();
        when(busMapper.toResponse(any(Bus.class))).thenReturn(respuesta);

        BusResponse resultado = servicio.crear(requestCrear(), adminSucursal);

        ArgumentCaptor<Bus> captor = ArgumentCaptor.forClass(Bus.class);
        verify(busRepository).saveAndFlush(captor.capture());
        assertEquals("P900ABC", captor.getValue().getPlaca());
        assertEquals(EstadoOperativo.DISPONIBLE, captor.getValue().getEstadoOperativo());
        assertEquals(BigDecimal.ZERO, captor.getValue().getKilometrajeActual());
        assertEquals(idSucursal, captor.getValue().getIdSucursal());
        verify(eventos).busRegistrado(captor.getValue());
        assertEquals(respuesta, resultado);
    }

    @Test
    void crearRechazaPlacaDuplicada() {
        sucursalOk();
        when(busRepository.existsByPlacaIgnoreCase("P900ABC")).thenReturn(true);
        assertThrows(PlacaDuplicadaException.class, () -> servicio.crear(requestCrear(), adminSucursal));
    }

    @Test
    void crearRechazaSucursalInactiva() {
        when(sucursalRefRepository.existsById(idSucursal)).thenReturn(true);
        when(sucursalRefRepository.existsByIdAndActivaTrue(idSucursal)).thenReturn(false);
        assertThrows(org.transportsgt.fleetservice.exception.SucursalInactivaException.class,
                () -> servicio.crear(requestCrear(), adminSucursal));
    }

    @Test
    void actualizarConVersionViejaLanzaConflicto() {
        when(busRepository.findById(idBus)).thenReturn(Optional.of(bus(3L)));
        ActualizarBusRequest request = new ActualizarBusRequest(
                "P900ABC", "Toyota", "Hiace", (short) 2019, (short) 45, 1L);
        assertThrows(RegistroModificadoException.class,
                () -> servicio.actualizar(idBus, request, adminSucursal));
    }

    @Test
    void actualizarRechazaPlacaDuplicada() {
        when(busRepository.findById(idBus)).thenReturn(Optional.of(bus(1L)));
        when(busRepository.existsByPlacaIgnoreCaseAndIdNot("P900ABC", idBus)).thenReturn(true);
        ActualizarBusRequest request = new ActualizarBusRequest(
                "P900ABC", "Toyota", "Hiace", (short) 2019, (short) 45, null);
        assertThrows(PlacaDuplicadaException.class, () -> servicio.actualizar(idBus, request, adminSucursal));
    }

    @Test
    void actualizarAplicaCambiosYPublica() {
        Bus entity = bus(1L);
        when(busRepository.findById(idBus)).thenReturn(Optional.of(entity));
        when(busRepository.existsByPlacaIgnoreCaseAndIdNot("P901ABC", idBus)).thenReturn(false);
        when(busRepository.saveAndFlush(entity)).thenAnswer(inv -> inv.getArgument(0));
        when(busMapper.toResponse(entity)).thenReturn(respuestaBus());
        ActualizarBusRequest request = new ActualizarBusRequest(
                " P901ABC ", "Mercedes-Benz", "O500", (short) 2021, (short) 52, 1L);

        servicio.actualizar(idBus, request, adminSucursal);

        assertEquals("P901ABC", entity.getPlaca());
        verify(busMapper).actualizar(entity, request);
        verify(eventos).busActualizado(entity);
    }

    @Test
    void desactivarSinViajesPublicaSoloSiCambia() {
        Bus entity = bus(1L);
        when(busRepository.findById(idBus)).thenReturn(Optional.of(entity));
        when(consultaViajes.hayViajesActivos(idBus)).thenReturn(false);
        when(busRepository.saveAndFlush(entity)).thenAnswer(inv -> inv.getArgument(0));
        when(busMapper.toResponse(entity)).thenReturn(respuestaBus());

        servicio.cambiarActivo(idBus, new CambiarActivoRequest(false, 1L), adminSucursal);

        org.junit.jupiter.api.Assertions.assertFalse(entity.getActivo());
        verify(eventos).busActualizado(entity);
    }

    @Test
    void desactivarConViajesActivosLanza409() {
        when(busRepository.findById(idBus)).thenReturn(Optional.of(bus(1L)));
        when(consultaViajes.hayViajesActivos(idBus)).thenReturn(true);
        assertThrows(BusConViajesException.class,
                () -> servicio.cambiarActivo(idBus, new CambiarActivoRequest(false, null), adminSucursal));
    }

    @Test
    void desactivarConViajesCaidosLanza503() {
        when(busRepository.findById(idBus)).thenReturn(Optional.of(bus(1L)));
        when(consultaViajes.hayViajesActivos(idBus))
                .thenThrow(new ViajesNoDisponibleException(new RuntimeException("caído")));
        assertThrows(ViajesNoDisponibleException.class,
                () -> servicio.cambiarActivo(idBus, new CambiarActivoRequest(false, null), adminSucursal));
    }

    @Test
    void sinCambioDeActivoNoPublicaEvento() {
        Bus entity = bus(1L);
        when(busRepository.findById(idBus)).thenReturn(Optional.of(entity));
        when(busMapper.toResponse(entity)).thenReturn(respuestaBus());

        servicio.cambiarActivo(idBus, new CambiarActivoRequest(true, 1L), adminSucursal);

        verify(eventos, never()).busActualizado(any());
        verify(busRepository, never()).saveAndFlush(any());
    }

    @Test
    void enTransitoNoSeCambiaAPeticion() {
        assertThrows(EstadoOperativoInvalidoException.class,
                () -> servicio.cambiarEstadoOperativo(idBus,
                        new CambiarEstadoOperativoRequest(EstadoOperativo.EN_TRANSITO, null), adminSucursal));
    }

    @Test
    void cambiarEstadoOperativoPublicaSoloSiCambia() {
        Bus entity = bus(1L);
        when(busRepository.findById(idBus)).thenReturn(Optional.of(entity));
        when(busRepository.saveAndFlush(entity)).thenAnswer(inv -> inv.getArgument(0));
        when(busMapper.toResponse(entity)).thenReturn(respuestaBus());

        servicio.cambiarEstadoOperativo(idBus,
                new CambiarEstadoOperativoRequest(EstadoOperativo.EN_TALLER, 1L), adminSucursal);

        assertEquals(EstadoOperativo.EN_TALLER, entity.getEstadoOperativo());
        verify(eventos).busActualizado(entity);
    }

    @Test
    void cambiarAlMismoEstadoOperativoNoPublica() {
        Bus entity = bus(1L);
        when(busRepository.findById(idBus)).thenReturn(Optional.of(entity));
        when(busMapper.toResponse(entity)).thenReturn(respuestaBus());

        servicio.cambiarEstadoOperativo(idBus,
                new CambiarEstadoOperativoRequest(EstadoOperativo.DISPONIBLE, null), adminSucursal);

        verify(eventos, never()).busActualizado(any());
        verify(busRepository, never()).saveAndFlush(any());
    }
}
