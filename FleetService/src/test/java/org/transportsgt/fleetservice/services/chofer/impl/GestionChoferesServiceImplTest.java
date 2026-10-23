package org.transportsgt.fleetservice.services.chofer.impl;

import org.transportsgt.fleetservice.repositories.BusRepository;
import org.transportsgt.fleetservice.services.comun.BuscadorFlota;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.eventos.payload.TipoLicencia;
import org.transportsgt.fleetservice.config.security.UsuarioAutenticado;
import org.transportsgt.fleetservice.dto.request.ActualizarChoferRequest;
import org.transportsgt.fleetservice.dto.request.CambiarActivoRequest;
import org.transportsgt.fleetservice.dto.request.CrearChoferRequest;
import org.transportsgt.fleetservice.dto.response.ChoferResponse;
import org.transportsgt.fleetservice.eventos.EventosFlota;
import org.transportsgt.fleetservice.exception.ChoferNoEncontradoException;
import org.transportsgt.fleetservice.exception.LicenciaDuplicadaException;
import org.transportsgt.fleetservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.fleetservice.exception.RegistroModificadoException;
import org.transportsgt.fleetservice.exception.UsuarioYaEnlazadoException;
import org.transportsgt.fleetservice.mappers.ChoferMapper;
import org.transportsgt.fleetservice.models.Chofer;
import org.transportsgt.fleetservice.repositories.ChoferRepository;
import org.transportsgt.fleetservice.repositories.SucursalRefRepository;
import org.transportsgt.fleetservice.services.autorizacion.AutorizacionSucursal;

import java.math.BigDecimal;
import java.time.LocalDate;
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
class GestionChoferesServiceImplTest {

    @Mock
    private ChoferRepository choferRepository;
    @Mock
    private SucursalRefRepository sucursalRefRepository;
    @Mock
    private ChoferMapper choferMapper;
    @Mock
    private EventosFlota eventos;

    private GestionChoferesServiceImpl servicio;

    private final UUID idSucursal = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private final UUID idChofer = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private final UsuarioAutenticado adminSucursal =
            new UsuarioAutenticado(UUID.randomUUID(), "admin.xela@transportes.gt",
                    Rol.ADMIN_SUCURSAL, idSucursal, "Admin Xela");
    private final UsuarioAutenticado adminSistema =
            new UsuarioAutenticado(UUID.randomUUID(), "admin@transportes.gt",
                    Rol.ADMIN_SISTEMA, null, "Admin Sistema");

    @Mock
    private BusRepository busRepository;

    @BeforeEach
    void setUp() {
        servicio = new GestionChoferesServiceImpl(choferRepository, new BuscadorFlota(busRepository, choferRepository),
                new AutorizacionSucursal(sucursalRefRepository), new ValidadorChofer(choferRepository),
                choferMapper, eventos);
    }

    private Chofer chofer(long version) {
        return Chofer.builder()
                .id(idChofer)
                .idSucursal(idSucursal)
                .nombreCompleto("Carlos Méndez")
                .correo("chofer@transportes.gt")
                .telefono("5555-1001")
                .numeroLicencia("LIC-GT-101")
                .tipoLicencia(TipoLicencia.A)
                .fechaVencimientoLicencia(LocalDate.of(2027, 3, 15))
                .salarioBaseViaje(new BigDecimal("180.00"))
                .activo(true)
                .version(version)
                .build();
    }

    private CrearChoferRequest requestCrear() {
        return new CrearChoferRequest(" Carlos Méndez ", "chofer@transportes.gt", "5555-1001",
                " LIC-GT-101 ", TipoLicencia.A, LocalDate.of(2027, 3, 15), new BigDecimal("180.00"), null);
    }

    private static ChoferResponse respuestaChofer() {
        return new ChoferResponse(null, null, null, null, null, null, null, null, null, null, null, true, null, null, null);
    }

    private void sucursalOk() {
        when(sucursalRefRepository.existsById(idSucursal)).thenReturn(true);
        when(sucursalRefRepository.existsByIdAndActivaTrue(idSucursal)).thenReturn(true);
    }

    @Test
    void crearGuardaYPublicaChoferRegistrado() {
        sucursalOk();
        when(choferRepository.existsByNumeroLicencia("LIC-GT-101")).thenReturn(false);
        when(choferRepository.saveAndFlush(any(Chofer.class))).thenAnswer(inv -> {
            Chofer guardado = inv.getArgument(0);
            guardado.setVersion(0L);
            return guardado;
        });
        when(choferMapper.toResponse(any(Chofer.class))).thenReturn(respuestaChofer());

        servicio.crear(requestCrear(), adminSucursal);

        ArgumentCaptor<Chofer> captor = ArgumentCaptor.forClass(Chofer.class);
        verify(choferRepository).saveAndFlush(captor.capture());
        assertEquals(idSucursal, captor.getValue().getIdSucursal());
        verify(eventos).choferRegistrado(captor.getValue());
    }

    @Test
    void crearRechazaLicenciaDuplicada() {
        sucursalOk();
        when(choferRepository.existsByNumeroLicencia("LIC-GT-101")).thenReturn(true);
        assertThrows(LicenciaDuplicadaException.class, () -> servicio.crear(requestCrear(), adminSucursal));
    }

    @Test
    void crearRechazaUsuarioYaEnlazado() {
        sucursalOk();
        when(choferRepository.existsByNumeroLicencia("LIC-GT-101")).thenReturn(false);
        UUID idUsuario = UUID.randomUUID();
        CrearChoferRequest conUsuario = new CrearChoferRequest("Carlos Méndez", "chofer@transportes.gt",
                "5555-1001", "LIC-GT-101", TipoLicencia.A, LocalDate.of(2027, 3, 15),
                new BigDecimal("180.00"), idUsuario);
        when(choferRepository.existsByIdUsuario(idUsuario)).thenReturn(true);
        assertThrows(UsuarioYaEnlazadoException.class, () -> servicio.crear(conUsuario, adminSucursal));
    }

    @Test
    void actualizarConVersionViejaLanzaConflicto() {
        when(choferRepository.findById(idChofer)).thenReturn(Optional.of(chofer(3L)));
        ActualizarChoferRequest request = new ActualizarChoferRequest("Carlos Méndez", "c@transportes.gt",
                "5555-1", "LIC-GT-101", TipoLicencia.A, LocalDate.of(2027, 3, 15),
                new BigDecimal("180.00"), null, 1L);
        assertThrows(RegistroModificadoException.class,
                () -> servicio.actualizar(idChofer, request, adminSucursal));
    }

    @Test
    void actualizarRechazaLicenciaDuplicada() {
        when(choferRepository.findById(idChofer)).thenReturn(Optional.of(chofer(1L)));
        when(choferRepository.existsByNumeroLicenciaAndIdNot("LIC-GT-999", idChofer)).thenReturn(true);
        ActualizarChoferRequest request = new ActualizarChoferRequest("Carlos Méndez", "c@transportes.gt",
                "5555-1", "LIC-GT-999", TipoLicencia.A, LocalDate.of(2027, 3, 15),
                new BigDecimal("180.00"), null, null);
        assertThrows(LicenciaDuplicadaException.class,
                () -> servicio.actualizar(idChofer, request, adminSucursal));
    }

    @Test
    void actualizarAplicaYPublicaChoferActualizado() {
        Chofer entity = chofer(1L);
        when(choferRepository.findById(idChofer)).thenReturn(Optional.of(entity));
        when(choferRepository.existsByNumeroLicenciaAndIdNot("LIC-GT-202", idChofer)).thenReturn(false);
        when(choferRepository.saveAndFlush(entity)).thenAnswer(inv -> inv.getArgument(0));
        when(choferMapper.toResponse(entity)).thenReturn(respuestaChofer());
        ActualizarChoferRequest request = new ActualizarChoferRequest("Carlos Méndez", "c@transportes.gt",
                "5555-1", "LIC-GT-202", TipoLicencia.B, LocalDate.of(2028, 1, 1),
                new BigDecimal("200.00"), null, 1L);

        servicio.actualizar(idChofer, request, adminSucursal);

        assertEquals("LIC-GT-202", entity.getNumeroLicencia());
        verify(choferMapper).actualizar(entity, request);
        verify(eventos).choferActualizado(entity);
    }

    @Test
    void desactivarSinCambioNoPublica() {
        Chofer entity = chofer(1L);
        when(choferRepository.findById(idChofer)).thenReturn(Optional.of(entity));
        when(choferMapper.toResponse(entity)).thenReturn(respuestaChofer());

        servicio.cambiarActivo(idChofer, new CambiarActivoRequest(true, 1L), adminSucursal);

        verify(eventos, never()).choferActualizado(any());
        verify(choferRepository, never()).saveAndFlush(any());
    }

    @Test
    void desactivarCambiaActivoYPublica() {
        Chofer entity = chofer(1L);
        when(choferRepository.findById(idChofer)).thenReturn(Optional.of(entity));
        when(choferRepository.saveAndFlush(entity)).thenAnswer(inv -> inv.getArgument(0));
        when(choferMapper.toResponse(entity)).thenReturn(respuestaChofer());

        servicio.cambiarActivo(idChofer, new CambiarActivoRequest(false, 1L), adminSucursal);

        org.junit.jupiter.api.Assertions.assertFalse(entity.getActivo());
        verify(eventos).choferActualizado(entity);
    }
}
