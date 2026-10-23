package org.transportsgt.tripservice.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.tripservice.config.security.UsuarioAutenticado;
import org.transportsgt.tripservice.dto.request.Requests;
import org.transportsgt.tripservice.eventos.EventosViaje;
import org.transportsgt.tripservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.tripservice.exception.ReglaNegocioException;
import org.transportsgt.tripservice.exception.RutaNoEncontradaException;
import org.transportsgt.tripservice.models.Ruta;
import org.transportsgt.tripservice.repositories.RutaRepository;
import org.transportsgt.tripservice.repositories.SucursalRefRepository;
import org.transportsgt.tripservice.repositories.ViajeRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
class RutaServiceTest {

    @Mock
    private RutaRepository rutaRepository;
    @Mock
    private ViajeRepository viajeRepository;
    @Mock
    private SucursalRefRepository sucursalRefRepository;
    @Mock
    private EventosViaje eventos;

    private RutaService servicio;

    private final UUID XELA = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private final UUID CAPITAL = UUID.fromString("10000000-0000-4000-8000-000000000002");
    private final UsuarioAutenticado admin = new UsuarioAutenticado(
            UUID.randomUUID(), "a@t.gt", Rol.ADMIN_SUCURSAL, XELA, "Admin");
    private final UsuarioAutenticado adminCapital = new UsuarioAutenticado(
            UUID.randomUUID(), "c@t.gt", Rol.ADMIN_SUCURSAL, CAPITAL, "Cap");

    @BeforeEach
    void setUp() {
        servicio = new RutaService(rutaRepository, viajeRepository, sucursalRefRepository, eventos);
    }

    private Ruta ruta() {
        return Ruta.builder().id(UUID.randomUUID()).idSucursalOrigen(XELA).idSucursalDestino(CAPITAL)
                .distanciaKm(new BigDecimal("200.00")).precioBoleto(new BigDecimal("80.00"))
                .creadoPor(admin.id()).version(1L)
                .creadoEn(OffsetDateTime.now()).actualizadoEn(OffsetDateTime.now()).build();
    }

    private Requests.RutaRequest request() {
        return new Requests.RutaRequest(XELA, CAPITAL, new BigDecimal("205.00"), new BigDecimal("85.00"));
    }

    @Test
    void crearPublicaRutaCreada() {
        Ruta r = ruta();
        when(sucursalRefRepository.existsByIdAndActivaTrue(XELA)).thenReturn(true);
        when(sucursalRefRepository.existsByIdAndActivaTrue(CAPITAL)).thenReturn(true);
        when(rutaRepository.existsByIdSucursalOrigenAndIdSucursalDestino(XELA, CAPITAL)).thenReturn(false);
        when(rutaRepository.saveAndFlush(any(Ruta.class))).thenReturn(r);

        var resp = servicio.crear(request(), admin);

        assertEquals(r.getId(), resp.id());
        verify(eventos).rutaCreada(any());
    }

    @Test
    void crearRechazaOrigenDistintoDeLaSucursal() {
        assertThrows(OperacionNoAutorizadaException.class, () -> servicio.crear(request(), adminCapital));
    }

    @Test
    void crearRechazaSucursalInactiva() {
        when(sucursalRefRepository.existsByIdAndActivaTrue(XELA)).thenReturn(true);
        when(sucursalRefRepository.existsByIdAndActivaTrue(CAPITAL)).thenReturn(false);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.crear(request(), admin));
        assertEquals("SUCURSAL_NO_ENCONTRADA", ex.getCodigo());
    }

    @Test
    void crearRechazaDuplicada() {
        when(sucursalRefRepository.existsByIdAndActivaTrue(XELA)).thenReturn(true);
        when(sucursalRefRepository.existsByIdAndActivaTrue(CAPITAL)).thenReturn(true);
        when(rutaRepository.existsByIdSucursalOrigenAndIdSucursalDestino(XELA, CAPITAL)).thenReturn(true);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.crear(request(), admin));
        assertEquals("RUTA_DUPLICADA", ex.getCodigo());
    }

    @Test
    void actualizarPublicaRutaActualizada() {
        Ruta r = ruta();
        when(rutaRepository.findById(r.getId())).thenReturn(Optional.of(r));
        when(rutaRepository.existsByIdSucursalOrigenAndIdSucursalDestinoAndIdNot(XELA, CAPITAL, r.getId()))
                .thenReturn(false);
        when(rutaRepository.saveAndFlush(any(Ruta.class))).thenReturn(r);

        var resp = servicio.actualizar(r.getId(), request(), admin);

        assertEquals(new BigDecimal("85.00"), resp.precioBoleto());
        verify(eventos).rutaActualizada(any());
    }

    @Test
    void actualizarRechazaCambioDeOrigen() {
        Ruta r = ruta();
        Requests.RutaRequest req = new Requests.RutaRequest(CAPITAL, XELA,
                new BigDecimal("10.00"), new BigDecimal("50.00"));
        when(rutaRepository.findById(r.getId())).thenReturn(Optional.of(r));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.actualizar(r.getId(), req, admin));
        assertEquals("RUTA_INVALIDA", ex.getCodigo());
    }

    @Test
    void actualizarRechazaRutaInexistente() {
        UUID id = UUID.randomUUID();
        when(rutaRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(RutaNoEncontradaException.class, () -> servicio.actualizar(id, request(), admin));
    }

    @Test
    void eliminarSinViajesPublicaRutaEliminada() {
        Ruta r = ruta();
        when(rutaRepository.findById(r.getId())).thenReturn(Optional.of(r));
        when(viajeRepository.existsByRutaId(r.getId())).thenReturn(false);

        servicio.eliminar(r.getId(), admin);

        verify(rutaRepository).delete(eq(r));
        verify(eventos).rutaEliminada(r.getId());
    }

    @Test
    void eliminarConViajesResponde409() {
        Ruta r = ruta();
        when(rutaRepository.findById(r.getId())).thenReturn(Optional.of(r));
        when(viajeRepository.existsByRutaId(r.getId())).thenReturn(true);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.eliminar(r.getId(), admin));
        assertEquals("RUTA_CON_VIAJES", ex.getCodigo());
        verify(rutaRepository, never()).delete(eq(r));
    }

    @Test
    void eliminarRechazaOtraSucursal() {
        Ruta r = ruta();
        when(rutaRepository.findById(r.getId())).thenReturn(Optional.of(r));
        assertThrows(OperacionNoAutorizadaException.class, () -> servicio.eliminar(r.getId(), adminCapital));
    }

    @Test
    void listarPorOrigen() {
        Ruta r = ruta();
        when(rutaRepository.findAllByIdSucursalOrigen(XELA)).thenReturn(List.of(r));
        assertEquals(1, servicio.listar(XELA, null).size());
    }

    @Test
    void listarTodos() {
        Ruta r = ruta();
        when(rutaRepository.findAll()).thenReturn(List.of(r));
        assertEquals(1, servicio.listar(null, null).size());
    }
}
