package org.transportsgt.operationalcostsservice.services.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.operationalcostsservice.config.security.authorization.PoliticaAccesoSucursal;
import org.transportsgt.operationalcostsservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.operationalcostsservice.exception.NoEncontradoException;
import org.transportsgt.operationalcostsservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.operationalcostsservice.mappers.CostosMapper;
import org.transportsgt.operationalcostsservice.models.GastoCombustible;
import org.transportsgt.operationalcostsservice.models.PagoChofer;
import org.transportsgt.operationalcostsservice.models.RegistroDepreciacion;
import org.transportsgt.operationalcostsservice.repositories.GastoCombustibleRepository;
import org.transportsgt.operationalcostsservice.repositories.PagoChoferRepository;
import org.transportsgt.operationalcostsservice.repositories.RegistroDepreciacionRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumenViajeServiceImplTest {

    @Mock GastoCombustibleRepository combustibleRepository;
    @Mock RegistroDepreciacionRepository depreciacionRepository;
    @Mock PagoChoferRepository pagoChoferRepository;
    @Mock CostosMapper mapper;

    private ResumenViajeServiceImpl servicio() {
        return new ResumenViajeServiceImpl(combustibleRepository, depreciacionRepository,
                pagoChoferRepository, mapper, new PoliticaAccesoSucursal());
    }

    private UsuarioAutenticado sis() {
        return new UsuarioAutenticado(UUID.randomUUID(), "sis@t.gt", Rol.ADMIN_SISTEMA, null, "SIS");
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
    void resumenViajeSUSSinCostosPropios403AntesQue404() {
        when(combustibleRepository.findByIdViaje(any())).thenReturn(Optional.empty());
        when(depreciacionRepository.findByIdViaje(any())).thenReturn(Optional.empty());
        when(pagoChoferRepository.findByIdViaje(any())).thenReturn(Optional.empty());
        var suc = new UsuarioAutenticado(UUID.randomUUID(), "suc@t.gt", Rol.ADMIN_SUCURSAL,
                UUID.randomUUID(), "SUC");

        assertThatThrownBy(() -> servicio().resumenViaje(UUID.randomUUID(), suc))
                .isInstanceOf(OperacionNoAutorizadaException.class);
    }

    @Test
    void resumenViajeSUCConCostosDeOtraSucursal403() {
        UUID idViaje = UUID.randomUUID();
        var ajena = new org.transportsgt.operationalcostsservice.models.GastoCombustible();
        ajena.setIdSucursal(UUID.randomUUID());
        when(combustibleRepository.findByIdViaje(idViaje)).thenReturn(Optional.of(ajena));
        var suc = new UsuarioAutenticado(UUID.randomUUID(), "suc@t.gt", Rol.ADMIN_SUCURSAL,
                UUID.randomUUID(), "SUC");

        assertThatThrownBy(() -> servicio().resumenViaje(idViaje, suc))
                .isInstanceOf(OperacionNoAutorizadaException.class);
    }

    @Test
    void resumenViajeConLosTresCostosLosIncluyeTodos() {
        UUID idViaje = UUID.randomUUID();
        GastoCombustible comb = new GastoCombustible();
        RegistroDepreciacion dep = new RegistroDepreciacion();
        PagoChofer pago = new PagoChofer();
        when(combustibleRepository.findByIdViaje(idViaje)).thenReturn(Optional.of(comb));
        when(depreciacionRepository.findByIdViaje(idViaje)).thenReturn(Optional.of(dep));
        when(pagoChoferRepository.findByIdViaje(idViaje)).thenReturn(Optional.of(pago));

        var resumen = servicio().resumenViaje(idViaje, sis());

        assertThat(resumen.idViaje()).isEqualTo(idViaje);
        verify(mapper).toCombustibleResponse(comb);
        verify(mapper).toDepreciacionResponse(dep);
        verify(mapper).toPagoChoferResponse(pago);
    }

    @Test
    void resumenViajeSUCConSoloDepreciacionDeSuSucursal() {
        UUID idViaje = UUID.randomUUID();
        UUID sucursal = UUID.randomUUID();
        RegistroDepreciacion dep = new RegistroDepreciacion();
        dep.setIdSucursal(sucursal);
        when(combustibleRepository.findByIdViaje(idViaje)).thenReturn(Optional.empty());
        when(depreciacionRepository.findByIdViaje(idViaje)).thenReturn(Optional.of(dep));
        when(pagoChoferRepository.findByIdViaje(idViaje)).thenReturn(Optional.empty());
        var suc = new UsuarioAutenticado(UUID.randomUUID(), "suc@t.gt", Rol.ADMIN_SUCURSAL, sucursal, "SUC");

        var resumen = servicio().resumenViaje(idViaje, suc);

        assertThat(resumen.combustible()).isNull();
        assertThat(resumen.pagoChofer()).isNull();
        verify(mapper).toDepreciacionResponse(dep);
    }

    @Test
    void resumenViajeSISConSoloPagoDeChofer() {
        UUID idViaje = UUID.randomUUID();
        PagoChofer pago = new PagoChofer();
        when(combustibleRepository.findByIdViaje(idViaje)).thenReturn(Optional.empty());
        when(depreciacionRepository.findByIdViaje(idViaje)).thenReturn(Optional.empty());
        when(pagoChoferRepository.findByIdViaje(idViaje)).thenReturn(Optional.of(pago));

        var resumen = servicio().resumenViaje(idViaje, sis());

        assertThat(resumen.depreciacion()).isNull();
        verify(mapper).toPagoChoferResponse(pago);
    }
}
