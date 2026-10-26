package org.transportsgt.operationalcostsservice.services.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.operationalcostsservice.config.security.PoliticaAccesoSucursal;
import org.transportsgt.operationalcostsservice.config.security.UsuarioAutenticado;
import org.transportsgt.operationalcostsservice.dto.request.TarifaDepreciacionRequest;
import org.transportsgt.operationalcostsservice.dto.response.TarifaDepreciacionResponse;
import org.transportsgt.operationalcostsservice.eventos.EventosCostos;
import org.transportsgt.operationalcostsservice.exception.NoEncontradoException;
import org.transportsgt.operationalcostsservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.operationalcostsservice.mappers.CostosMapper;
import org.transportsgt.operationalcostsservice.models.ConfiguracionDepreciacion;
import org.transportsgt.operationalcostsservice.repositories.ConfiguracionDepreciacionRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TarifaDepreciacionServiceImplTest {

    @Mock ConfiguracionDepreciacionRepository tarifaRepository;
    @Mock CostosMapper mapper;
    @Mock EventosCostos eventos;

    private final Clock fijo = Clock.fixed(LocalDate.of(2026, 10, 17)
            .atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private TarifaDepreciacionServiceImpl servicio() {
        return new TarifaDepreciacionServiceImpl(tarifaRepository, mapper, eventos, fijo,
                new PoliticaAccesoSucursal());
    }

    private UsuarioAutenticado sis() {
        return new UsuarioAutenticado(UUID.randomUUID(), "sis@t.gt", Rol.ADMIN_SISTEMA, null, "SIS");
    }

    private UsuarioAutenticado suc() {
        return new UsuarioAutenticado(UUID.randomUUID(), "suc@t.gt", Rol.ADMIN_SUCURSAL,
                UUID.randomUUID(), "SUC");
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
                new TarifaDepreciacionResponse(
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
                suc()))
                .isInstanceOf(OperacionNoAutorizadaException.class);
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
    void cambiarTarifaGuardaYPublica() {
        UUID idUsuario = UUID.randomUUID();
        LocalDate desde = LocalDate.of(2026, 9, 1);
        ConfiguracionDepreciacion t = ConfiguracionDepreciacion.builder()
                .id(UUID.randomUUID()).montoPorKm(new BigDecimal("2.50"))
                .vigenteDesde(desde).creadoPor(idUsuario).build();
        when(tarifaRepository.findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(desde))
                .thenReturn(Optional.of(t));
        when(mapper.toResponse(t)).thenReturn(
                new TarifaDepreciacionResponse(
                        t.getId(), t.getMontoPorKm(), t.getVigenteDesde(), t.getCreadoPor(), null));

        var resp = servicio().cambiarTarifa(
                new TarifaDepreciacionRequest(new BigDecimal("2.50"), desde), sis());
        verify(tarifaRepository).guardarTarifa(eq(new BigDecimal("2.50")), eq(desde), any(UUID.class));
        verify(eventos).tarifaDepreciacionCambiada(any());
        assertThat(resp).isNotNull();
    }
}
