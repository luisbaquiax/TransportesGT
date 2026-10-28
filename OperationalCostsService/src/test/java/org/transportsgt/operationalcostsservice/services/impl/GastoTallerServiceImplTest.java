package org.transportsgt.operationalcostsservice.services.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.eventos.payload.costos.TipoLinea;
import org.transportsgt.operationalcostsservice.config.security.authorization.PoliticaAccesoSucursal;
import org.transportsgt.operationalcostsservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.operationalcostsservice.dto.request.GastoTallerRequest;
import org.transportsgt.operationalcostsservice.eventos.EventosCostos;
import org.transportsgt.operationalcostsservice.exception.NoEncontradoException;
import org.transportsgt.operationalcostsservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.operationalcostsservice.exception.ValidacionException;
import org.transportsgt.operationalcostsservice.mappers.CostosMapper;
import org.transportsgt.operationalcostsservice.models.BusRef;
import org.transportsgt.operationalcostsservice.models.CategoriaGasto;
import org.transportsgt.operationalcostsservice.models.GastoTaller;
import org.transportsgt.operationalcostsservice.repositories.BusRefRepository;
import org.transportsgt.operationalcostsservice.repositories.CategoriaGastoRepository;
import org.transportsgt.operationalcostsservice.repositories.GastoTallerRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GastoTallerServiceImplTest {

    @Mock BusRefRepository busRefRepository;
    @Mock CategoriaGastoRepository categoriaRepository;
    @Mock GastoTallerRepository gastoTallerRepository;
    @Mock CostosMapper mapper;
    @Mock EventosCostos eventos;

    private GastoTallerServiceImpl servicio() {
        return new GastoTallerServiceImpl(busRefRepository, categoriaRepository,
                gastoTallerRepository, mapper, eventos, new PoliticaAccesoSucursal());
    }

    private UsuarioAutenticado sis() {
        return new UsuarioAutenticado(UUID.randomUUID(), "sis@t.gt", Rol.ADMIN_SISTEMA, null, "SIS");
    }

    private UsuarioAutenticado suc(UUID idSucursal) {
        return new UsuarioAutenticado(UUID.randomUUID(), "suc@t.gt", Rol.ADMIN_SUCURSAL, idSucursal, "SUC");
    }

    @Test
    void registrarTallerCalculaTotal() {
        UUID idBus = UUID.randomUUID();
        UUID idSucursal = UUID.randomUUID();
        UUID idCat = UUID.randomUUID();
        UUID idUsuario = UUID.randomUUID();

        BusRef bus = BusRef.builder().id(idBus).idSucursal(idSucursal)
                .placa("P1").activo(true).version(1L).build();
        when(busRefRepository.findById(idBus)).thenReturn(Optional.of(bus));
        when(categoriaRepository.findById(idCat)).thenReturn(Optional.of(
                CategoriaGasto.builder().id(idCat).nombre("Frenos").activa(true).build()));
        when(gastoTallerRepository.save(any())).thenAnswer(inv -> {
            GastoTaller g = inv.getArgument(0);
            g.setId(UUID.randomUUID());
            return g;
        });

        var request = new GastoTallerRequest(idBus, LocalDate.of(2026, 10, 1), null, List.of(
                new GastoTallerRequest.LineaTallerRequest(idCat, "Pastillas", TipoLinea.REPUESTO,
                        new BigDecimal("1000.00")),
                new GastoTallerRequest.LineaTallerRequest(idCat, "Instalación", TipoLinea.MANO_DE_OBRA,
                        new BigDecimal("250.00"))));

        var usuario = new UsuarioAutenticado(idUsuario, "s@t.gt", Rol.ADMIN_SUCURSAL, idSucursal, "SUC");
        servicio().registrarGastoTaller(request, usuario);

        ArgumentCaptor<GastoTaller> captor = ArgumentCaptor.forClass(GastoTaller.class);
        verify(gastoTallerRepository).save(captor.capture());
        assertThat(captor.getValue().getTotal()).isEqualByComparingTo("1250.00");
        assertThat(captor.getValue().getIdSucursal()).isEqualTo(idSucursal);
    }

    @Test
    void registrarTallerBusDeOtraSucursal403() {
        UUID idBus = UUID.randomUUID();
        BusRef bus = BusRef.builder().id(idBus).idSucursal(UUID.randomUUID())
                .placa("P1").activo(true).version(1L).build();
        when(busRefRepository.findById(idBus)).thenReturn(Optional.of(bus));

        var request = new GastoTallerRequest(idBus, LocalDate.of(2026, 10, 1), null, List.of(
                new GastoTallerRequest.LineaTallerRequest(UUID.randomUUID(), "x", TipoLinea.REPUESTO,
                        BigDecimal.ONE)));

        assertThatThrownBy(() -> servicio().registrarGastoTaller(request, suc(UUID.randomUUID())))
                .isInstanceOf(OperacionNoAutorizadaException.class);
    }

    @Test
    void registrarTallerBusInactivo400() {
        UUID idBus = UUID.randomUUID();
        BusRef bus = BusRef.builder().id(idBus).idSucursal(UUID.randomUUID())
                .placa("P1").activo(false).version(1L).build();
        when(busRefRepository.findById(idBus)).thenReturn(Optional.of(bus));

        var request = new GastoTallerRequest(idBus, LocalDate.of(2026, 10, 1), null, List.of(
                new GastoTallerRequest.LineaTallerRequest(UUID.randomUUID(), "x", TipoLinea.REPUESTO,
                        BigDecimal.ONE)));

        assertThatThrownBy(() -> servicio().registrarGastoTaller(request, sis()))
                .isInstanceOf(ValidacionException.class)
                .satisfies(ex -> assertThat(((ValidacionException) ex).getCodigo())
                        .isEqualTo("BUS_INACTIVO"));
    }

    @Test
    void listarGastosSUCSeLimitaASuSucursal() {
        UUID idSucursal = UUID.randomUUID();
        when(gastoTallerRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(Page.empty());

        servicio().listarGastosTaller(null, null, null, suc(idSucursal), PageRequest.of(0, 20));

        verify(gastoTallerRepository).findAll(any(Specification.class), any(PageRequest.class));
    }

    @Test
    void detalleGastoNoEncontrado() {
        when(gastoTallerRepository.findConLineasById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio().detalleGastoTaller(UUID.randomUUID(), sis()))
                .isInstanceOf(NoEncontradoException.class);
    }

    @Test
    void detalleGastoDeOtraSucursal403() {
        UUID id = UUID.randomUUID();
        GastoTaller g = GastoTaller.builder().id(id).idBus(UUID.randomUUID())
                .idSucursal(UUID.randomUUID()).fechaServicio(LocalDate.of(2026, 1, 1))
                .total(BigDecimal.ONE).registradoPor(UUID.randomUUID()).build();
        when(gastoTallerRepository.findConLineasById(id)).thenReturn(Optional.of(g));
        assertThatThrownBy(() -> servicio().detalleGastoTaller(id, suc(UUID.randomUUID())))
                .isInstanceOf(OperacionNoAutorizadaException.class);
    }
}
