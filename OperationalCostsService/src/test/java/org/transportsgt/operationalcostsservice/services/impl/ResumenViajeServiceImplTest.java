package org.transportsgt.operationalcostsservice.services.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.operationalcostsservice.config.security.PoliticaAccesoSucursal;
import org.transportsgt.operationalcostsservice.config.security.UsuarioAutenticado;
import org.transportsgt.operationalcostsservice.exception.NoEncontradoException;
import org.transportsgt.operationalcostsservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.operationalcostsservice.mappers.CostosMapper;
import org.transportsgt.operationalcostsservice.repositories.GastoCombustibleRepository;
import org.transportsgt.operationalcostsservice.repositories.PagoChoferRepository;
import org.transportsgt.operationalcostsservice.repositories.RegistroDepreciacionRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
}
