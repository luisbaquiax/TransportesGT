package org.transportsgt.fleetservice.services.consulta.impl;

import org.transportsgt.fleetservice.repositories.BusSpecs;
import org.transportsgt.fleetservice.repositories.ChoferSpecs;
import org.transportsgt.fleetservice.mappers.FabricaPaginas;
import org.transportsgt.fleetservice.services.comun.BuscadorFlota;
import org.transportsgt.fleetservice.repositories.ChoferRepository;
import org.transportsgt.fleetservice.mappers.ChoferMapper;
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
import org.transportsgt.eventos.payload.EstadoOperativo;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.fleetservice.clients.ConsultaViajes;
import org.transportsgt.fleetservice.config.security.UsuarioAutenticado;
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
class ConsultaBusesTest {

    @Mock
    private BusRepository busRepository;
    @Mock
    private SucursalRefRepository sucursalRefRepository;
    @Mock
    private BusMapper busMapper;

    private ConsultaFlotaServiceImpl servicio;

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
    @Mock
    private ChoferMapper choferMapper;

    @BeforeEach
    void setUp() {
        servicio = new ConsultaFlotaServiceImpl(busRepository, choferRepository, new BusSpecs(), new ChoferSpecs(),
                new BuscadorFlota(busRepository, choferRepository), new AutorizacionSucursal(sucursalRefRepository),
                busMapper, choferMapper, new FabricaPaginas());
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
    void listarAdminSucursalRestringeASuSucursal() {
        when(busRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(bus(0L))));
        when(busMapper.toResponse(any(Bus.class))).thenReturn(respuestaBus());

        PaginaResponse<BusResponse> pagina =
                servicio.listarBuses(null, null, null, null, Pageable.unpaged(), adminSucursal);

        assertEquals(1, pagina.totalElementos());
    }

    @Test
    void listarAdminSucursalConOtraSucursalLanza403() {
        UUID otra = UUID.fromString("10000000-0000-4000-8000-000000000002");
        assertThrows(OperacionNoAutorizadaException.class,
                () -> servicio.listarBuses(otra, null, null, null, Pageable.unpaged(), adminSucursal));
    }

    @Test
    void obtenerDevuelveBus() {
        when(busRepository.findById(idBus)).thenReturn(Optional.of(bus(0L)));
        when(busMapper.toResponse(any(Bus.class))).thenReturn(respuestaBus());

        assertNotNull(servicio.obtenerBus(idBus, adminSistema));
    }

    @Test
    void obtenerInexistenteLanza404() {
        when(busRepository.findById(idBus)).thenReturn(Optional.empty());
        assertThrows(BusNoEncontradoException.class, () -> servicio.obtenerBus(idBus, adminSistema));
    }

    @Test
    void obtenerDeOtraSucursalLanza403() {
        when(busRepository.findById(idBus)).thenReturn(Optional.of(bus(0L)));
        UsuarioAutenticado deOtra = new UsuarioAutenticado(UUID.randomUUID(), "x@transportes.gt",
                Rol.ADMIN_SUCURSAL, UUID.randomUUID(), "Otra");
        assertThrows(OperacionNoAutorizadaException.class, () -> servicio.obtenerBus(idBus, deOtra));
    }
}
