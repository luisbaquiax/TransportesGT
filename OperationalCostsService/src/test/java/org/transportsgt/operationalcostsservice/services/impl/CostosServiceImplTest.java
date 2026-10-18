package org.transportsgt.operationalcostsservice.services.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.payload.TipoLinea;
import org.transportsgt.operationalcostsservice.config.security.UsuarioAutenticado;
import org.transportsgt.operationalcostsservice.dto.request.CategoriaRequest;
import org.transportsgt.operationalcostsservice.dto.request.GastoTallerRequest;
import org.transportsgt.operationalcostsservice.dto.request.TarifaDepreciacionRequest;
import org.transportsgt.operationalcostsservice.eventos.EventosCostos;
import org.transportsgt.operationalcostsservice.exception.NoEncontradoException;
import org.transportsgt.operationalcostsservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.operationalcostsservice.exception.ReglaNegocioException;
import org.transportsgt.operationalcostsservice.exception.ValidacionException;
import org.transportsgt.operationalcostsservice.mappers.CostosMapper;
import org.transportsgt.operationalcostsservice.models.BusRef;
import org.transportsgt.operationalcostsservice.models.CategoriaGasto;
import org.transportsgt.operationalcostsservice.models.ConfiguracionDepreciacion;
import org.transportsgt.operationalcostsservice.models.GastoTaller;
import org.transportsgt.operationalcostsservice.repositories.BusRefRepository;
import org.transportsgt.operationalcostsservice.repositories.CategoriaGastoRepository;
import org.transportsgt.operationalcostsservice.repositories.ConfiguracionDepreciacionRepository;
import org.transportsgt.operationalcostsservice.repositories.GastoCombustibleRepository;
import org.transportsgt.operationalcostsservice.repositories.GastoTallerLineaRepository;
import org.transportsgt.operationalcostsservice.repositories.GastoTallerRepository;
import org.transportsgt.operationalcostsservice.repositories.PagoChoferRepository;
import org.transportsgt.operationalcostsservice.repositories.RegistroDepreciacionRepository;
import org.transportsgt.eventos.payload.Rol;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
class CostosServiceImplTest {

    @Mock ConfiguracionDepreciacionRepository tarifaRepository;
    @Mock CategoriaGastoRepository categoriaRepository;
    @Mock GastoTallerRepository gastoTallerRepository;
    @Mock GastoTallerLineaRepository gastoTallerLineaRepository;
    @Mock BusRefRepository busRefRepository;
    @Mock GastoCombustibleRepository combustibleRepository;
    @Mock RegistroDepreciacionRepository depreciacionRepository;
    @Mock PagoChoferRepository pagoChoferRepository;
    @Mock CostosMapper mapper;
    @Mock EventosCostos eventos;

    private final Clock fijo = Clock.fixed(LocalDate.of(2026, 10, 17)
            .atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private CostosServiceImpl servicio() {
        return new CostosServiceImpl(tarifaRepository, categoriaRepository, gastoTallerRepository,
                gastoTallerLineaRepository, busRefRepository, combustibleRepository,
                depreciacionRepository, pagoChoferRepository, mapper, eventos, fijo);
    }

    private UsuarioAutenticado sis() {
        return new UsuarioAutenticado(UUID.randomUUID(), "sis@t.gt", Rol.ADMIN_SISTEMA, null, "SIS");
    }

    private UsuarioAutenticado suc(UUID idSucursal) {
        return new UsuarioAutenticado(UUID.randomUUID(), "suc@t.gt", Rol.ADMIN_SUCURSAL, idSucursal, "SUC");
    }

    @Test
    void tarifaVigenteHoy() {
        UUID id = UUID.randomUUID();
        ConfiguracionDepreciacion tarifa = ConfiguracionDepreciacion.builder()
                .id(id).montoPorKm(new BigDecimal("2.00")).vigenteDesde(LocalDate.of(2026, 1, 1))
                .creadoPor(UUID.randomUUID()).build();
        when(tarifaRepository.findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(any()))
                .thenReturn(Optional.of(tarifa));
        when(mapper.toResponse(tarifa)).thenReturn(
                new org.transportsgt.operationalcostsservice.dto.response.TarifaDepreciacionResponse(
                        id, tarifa.getMontoPorKm(), tarifa.getVigenteDesde(), tarifa.getCreadoPor(), null));

        var resp = servicio().tarifaVigente(null);
        assertThat(resp).isNotNull();
        verify(mapper).toResponse(tarifa);
    }

    @Test
    void tarifaVigenteSinTarifaLanza404() {
        when(tarifaRepository.findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(any()))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio().tarifaVigente(LocalDate.of(2020, 1, 1)))
                .isInstanceOf(NoEncontradoException.class)
                .satisfies(ex -> assertThat(((NoEncontradoException) ex).getCodigo()).isEqualTo("SIN_TARIFA"));
    }

    @Test
    void cambiarTarifaSoloSistema() {
        assertThatThrownBy(() -> servicio().cambiarTarifa(
                new TarifaDepreciacionRequest(new BigDecimal("3"), LocalDate.of(2026, 1, 1)),
                suc(UUID.randomUUID())))
                .isInstanceOf(OperacionNoAutorizadaException.class);
    }

    @Test
    void crearCategoriaDuplicadaLanza409() {
        when(categoriaRepository.existsByNombreIgnoreCase("Llantas")).thenReturn(true);
        assertThatThrownBy(() -> servicio().crearCategoria(new CategoriaRequest("Llantas", null)))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(ex -> assertThat(((ReglaNegocioException) ex).getCodigo())
                        .isEqualTo("CATEGORIA_DUPLICADA"));
    }

    @Test
    void eliminarCategoriaConGastosLanza409() {
        UUID id = UUID.randomUUID();
        when(categoriaRepository.findById(id)).thenReturn(Optional.of(
                CategoriaGasto.builder().id(id).nombre("Llantas").activa(true).build()));
        when(gastoTallerLineaRepository.existsByCategoriaId(id)).thenReturn(true);

        assertThatThrownBy(() -> servicio().eliminarCategoria(id))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(ex -> assertThat(((ReglaNegocioException) ex).getCodigo())
                        .isEqualTo("CATEGORIA_CON_GASTOS"));
        verify(categoriaRepository, never()).delete(any());
    }

    @Test
    void eliminarCategoriaSinGastos() {
        UUID id = UUID.randomUUID();
        CategoriaGasto c = CategoriaGasto.builder().id(id).nombre("Tmp").activa(true).build();
        when(categoriaRepository.findById(id)).thenReturn(Optional.of(c));
        when(gastoTallerLineaRepository.existsByCategoriaId(id)).thenReturn(false);

        servicio().eliminarCategoria(id);
        verify(categoriaRepository).delete(c);
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
    void historialTarifas() {
        ConfiguracionDepreciacion t = ConfiguracionDepreciacion.builder()
                .id(UUID.randomUUID()).montoPorKm(new BigDecimal("2"))
                .vigenteDesde(LocalDate.of(2026, 1, 1)).creadoPor(UUID.randomUUID()).build();
        when(tarifaRepository.findAllByOrderByVigenteDesdeDesc()).thenReturn(List.of(t));
        servicio().historialTarifas();
        verify(mapper).toTarifaList(List.of(t));
    }

    @Test
    void listarCategoriasSoloActivas() {
        CategoriaGasto c = CategoriaGasto.builder().id(UUID.randomUUID())
                .nombre("Llantas").activa(true).build();
        when(categoriaRepository.findAllByActivaTrueOrderByNombre()).thenReturn(List.of(c));
        servicio().listarCategorias(true);
        verify(mapper).toCategoriaList(List.of(c));
    }

    @Test
    void actualizarCategoriaNoEncontrada() {
        when(categoriaRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio().actualizarCategoria(UUID.randomUUID(),
                new CategoriaRequest("x", null)))
                .isInstanceOf(NoEncontradoException.class);
    }

    @Test
    void cambiarActivaNoEncontrada() {
        when(categoriaRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio().cambiarActivaCategoria(UUID.randomUUID(),
                new org.transportsgt.operationalcostsservice.dto.request.CategoriaActivaRequest(false)))
                .isInstanceOf(NoEncontradoException.class);
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

    @Test
    void resumenViajeNoEncontrado() {
        when(combustibleRepository.findByIdViaje(any())).thenReturn(Optional.empty());
        when(depreciacionRepository.findByIdViaje(any())).thenReturn(Optional.empty());
        when(pagoChoferRepository.findByIdViaje(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio().resumenViaje(UUID.randomUUID(), sis()))
                .isInstanceOf(NoEncontradoException.class);
    }

    @Test
    void cambiarTarifaGuardaYPublica() {
        UUID idUsuario = UUID.randomUUID();
        LocalDate desde = LocalDate.of(2026, 9, 1);
        ConfiguracionDepreciacion t = ConfiguracionDepreciacion.builder()
                .id(UUID.randomUUID()).montoPorKm(new BigDecimal("2.50"))
                .vigenteDesde(desde).creadoPor(idUsuario).build();
        when(tarifaRepository.findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(desde))
                .thenReturn(Optional.of(t));
        when(mapper.toResponse(t)).thenReturn(
                new org.transportsgt.operationalcostsservice.dto.response.TarifaDepreciacionResponse(
                        t.getId(), t.getMontoPorKm(), t.getVigenteDesde(), t.getCreadoPor(), null));

        var resp = servicio().cambiarTarifa(
                new TarifaDepreciacionRequest(new BigDecimal("2.50"), desde), sis());
        verify(tarifaRepository).guardarTarifa(eq(new BigDecimal("2.50")), eq(desde), any(UUID.class));
        verify(eventos).tarifaDepreciacionCambiada(any());
        assertThat(resp).isNotNull();
    }
}
