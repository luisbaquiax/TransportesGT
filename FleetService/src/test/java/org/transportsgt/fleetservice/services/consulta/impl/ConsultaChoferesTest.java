package org.transportsgt.fleetservice.services.consulta.impl;

import org.transportsgt.fleetservice.repositories.BusSpecs;
import org.transportsgt.fleetservice.repositories.ChoferSpecs;
import org.transportsgt.fleetservice.mappers.FabricaPaginas;
import org.transportsgt.fleetservice.services.comun.BuscadorFlota;
import org.transportsgt.fleetservice.repositories.BusRepository;
import org.transportsgt.fleetservice.mappers.BusMapper;
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
class ConsultaChoferesTest {

    @Mock
    private ChoferRepository choferRepository;
    @Mock
    private SucursalRefRepository sucursalRefRepository;
    @Mock
    private ChoferMapper choferMapper;

    private ConsultaFlotaServiceImpl servicio;

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
    @Mock
    private BusMapper busMapper;

    @BeforeEach
    void setUp() {
        servicio = new ConsultaFlotaServiceImpl(busRepository, choferRepository, new BusSpecs(), new ChoferSpecs(),
                new BuscadorFlota(busRepository, choferRepository), new AutorizacionSucursal(sucursalRefRepository),
                busMapper, choferMapper, new FabricaPaginas());
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
    void listarRestringeASucursalDelToken() {
        when(choferRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(chofer(0L))));
        when(choferMapper.toResponse(any(Chofer.class))).thenReturn(respuestaChofer());

        var pagina = servicio.listarChoferes(null, null, null, Pageable.unpaged(), adminSucursal);

        assertEquals(1, pagina.totalElementos());
    }

    @Test
    void listarOtraSucursalLanza403() {
        UUID otra = UUID.fromString("10000000-0000-4000-8000-000000000002");
        assertThrows(OperacionNoAutorizadaException.class,
                () -> servicio.listarChoferes(otra, null, null, Pageable.unpaged(), adminSucursal));
    }

    @Test
    void obtenerInexistenteLanza404() {
        when(choferRepository.findById(idChofer)).thenReturn(Optional.empty());
        assertThrows(ChoferNoEncontradoException.class, () -> servicio.obtenerChofer(idChofer, adminSistema));
    }

    @Test
    void obtenerDeOtraSucursalLanza403() {
        when(choferRepository.findById(idChofer)).thenReturn(Optional.of(chofer(0L)));
        UsuarioAutenticado deOtra = new UsuarioAutenticado(UUID.randomUUID(), "x@transportes.gt",
                Rol.ADMIN_SUCURSAL, UUID.randomUUID(), "Otra");
        assertThrows(OperacionNoAutorizadaException.class, () -> servicio.obtenerChofer(idChofer, deOtra));
    }
}
