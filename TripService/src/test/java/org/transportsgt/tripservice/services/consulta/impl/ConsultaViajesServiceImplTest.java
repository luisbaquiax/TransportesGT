package org.transportsgt.tripservice.services.consulta.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.payload.viajes.TipoViaje;
import org.transportsgt.tripservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.tripservice.exception.SolicitudInvalidaException;
import org.transportsgt.tripservice.exception.ViajeNoEncontradoException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.transportsgt.tripservice.dto.request.Requests;
import org.transportsgt.tripservice.mappers.EnsambladorViajes;
import org.transportsgt.tripservice.mappers.FabricaPaginas;
import org.transportsgt.tripservice.mappers.ViajeMapperImpl;
import org.transportsgt.tripservice.models.Viaje;
import org.transportsgt.tripservice.repositories.BusRefRepository;
import org.transportsgt.tripservice.repositories.ChoferRefRepository;
import org.transportsgt.tripservice.repositories.RegistroLlegadaRepository;
import org.transportsgt.tripservice.repositories.RegistroSalidaRepository;
import org.transportsgt.tripservice.repositories.ViajeRepository;
import org.transportsgt.tripservice.repositories.ViajeSpecs;
import org.transportsgt.tripservice.services.autorizacion.AutorizacionViaje;
import org.transportsgt.tripservice.services.comun.BuscadorViajes;
import org.transportsgt.tripservice.services.viaje.impl.FixturesViaje;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultaViajesServiceImplTest {

    @Mock
    private ViajeRepository viajeRepository;
    @Mock
    private BusRefRepository busRefRepository;
    @Mock
    private ChoferRefRepository choferRefRepository;
    @Mock
    private RegistroSalidaRepository registroSalidaRepository;
    @Mock
    private RegistroLlegadaRepository registroLlegadaRepository;

    private final FixturesViaje f = new FixturesViaje();
    private ConsultaViajesServiceImpl servicio;

    @BeforeEach
    void setUp() {
        servicio = new ConsultaViajesServiceImpl(viajeRepository, new ViajeSpecs(), new FabricaPaginas(),
                new BuscadorViajes(viajeRepository),
                new AutorizacionViaje(choferRefRepository),
                new EnsambladorViajes(busRefRepository, choferRefRepository, registroSalidaRepository,
                        registroLlegadaRepository, new ViajeMapperImpl()));
    }

    @Test
    void choferNoPuedeVerViajeAjeno() {
        Viaje v = f.viajeProgramado();
        v.setIdChofer(UUID.randomUUID());
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));
        when(choferRefRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(OperacionNoAutorizadaException.class, () -> servicio.detalle(v.getId(), f.chofer));
    }

    @Test
    void choferSiPuedeVerSuViaje() {
        Viaje v = f.viajeProgramado();
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));
        when(choferRefRepository.findById(f.chofer1)).thenReturn(Optional.of(f.choferRef()));

        var resp = servicio.detalle(v.getId(), f.chofer);

        assertEquals(v.getId(), resp.id());
    }

    @Test
    void viajeInexistenteLanza404() {
        UUID id = UUID.randomUUID();
        when(viajeRepository.findConRutaById(id)).thenReturn(Optional.empty());

        assertThrows(ViajeNoEncontradoException.class, () -> servicio.detalle(id, f.adminSistema));
    }

    @Test
    void listarDevuelveLaPaginaOrdenadaPorSalida() {
        Viaje v = f.viajeProgramado();
        ArgumentCaptor<Pageable> pedido = ArgumentCaptor.forClass(Pageable.class);
        when(viajeRepository.findAll(any(Specification.class), pedido.capture()))
                .thenReturn(new PageImpl<>(List.of(v), PageRequest.of(1, 10), 11));

        var pagina = servicio.listar(new Requests.FiltroViajes(TipoViaje.REGULAR, null, null, null, null, null, null),
                1, 10, f.adminSistema);

        // El filtrado y la visibilidad se resuelven en la consulta (ViajeSpecs, prueba de integración)
        assertEquals(1, pagina.contenido().size());
        assertEquals(v.getId(), pagina.contenido().get(0).id());
        assertEquals(1, pagina.pagina());
        assertEquals(11, pagina.totalElementos());
        assertEquals(2, pagina.totalPaginas());
        assertEquals(10, pedido.getValue().getPageSize());
        assertEquals(Sort.by("fechaHoraSalida").and(Sort.by("id")), pedido.getValue().getSort());
    }

    @Test
    void listarSinResultadosDevuelvePaginaVacia() {
        when(viajeRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var pagina = servicio.listar(new Requests.FiltroViajes(null, null, null, null, null, null, null),
                0, 20, f.adminSucursal);

        assertTrue(pagina.contenido().isEmpty());
        assertEquals(0, pagina.totalElementos());
    }

    @Test
    void activosPorBusUsaEstadosProgramadoYEnTransito() {
        Viaje v = f.viajeProgramado();
        when(viajeRepository.findAllByIdBusAndEstadoInOrderByFechaHoraSalida(eq(f.bus1), any()))
                .thenReturn(List.of(v));

        var activos = servicio.activosPorBus(f.bus1);

        assertEquals(1, activos.size());
        verify(viajeRepository).findAllByIdBusAndEstadoInOrderByFechaHoraSalida(eq(f.bus1),
                any(java.util.Set.class));
    }

    @Test
    void resumenSinIdsEsInvalido() {
        assertThrows(SolicitudInvalidaException.class, () -> servicio.resumen(null));
        assertThrows(SolicitudInvalidaException.class, () -> servicio.resumen(Arrays.asList(null, null)));
        verify(viajeRepository, never()).findAllConRutaByIdInOrderByFechaHoraSalida(any());
    }

    @Test
    void resumenConMasDelMaximoEsInvalido() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i <= ConsultaViajesServiceImpl.MAX_IDS_RESUMEN; i++) {
            ids.add(UUID.randomUUID());
        }
        assertThrows(SolicitudInvalidaException.class, () -> servicio.resumen(ids));
    }

    @Test
    void resumenQuitaDuplicadosYNulos() {
        UUID id = UUID.randomUUID();
        when(viajeRepository.findAllConRutaByIdInOrderByFechaHoraSalida(java.util.Set.of(id))).thenReturn(List.of());

        assertTrue(servicio.resumen(Arrays.asList(id, null, id)).isEmpty());
    }
}
