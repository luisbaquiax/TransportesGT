package org.transportsgt.privaterentalservice.services.tarifa.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.transportsgt.privaterentalservice.dto.request.TarifaRequest;
import org.transportsgt.privaterentalservice.dto.response.TarifaResponse;
import org.transportsgt.privaterentalservice.exception.AlquilerException;
import org.transportsgt.privaterentalservice.exception.SolicitudInvalidaException;
import org.transportsgt.privaterentalservice.mappers.TarifaMapper;
import org.transportsgt.privaterentalservice.models.TarifaAlquiler;
import org.transportsgt.privaterentalservice.repositories.TarifaAlquilerRepository;
import org.transportsgt.privaterentalservice.soporte.DatosPrueba;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TarifaAlquilerServiceImplTest {

    @Mock
    private TarifaAlquilerRepository tarifaRepository;
    @Mock
    private TarifaMapper mapper;

    private final DatosPrueba datos = new DatosPrueba();
    private TarifaAlquilerServiceImpl servicio;

    @BeforeEach
    void setUp() {
        servicio = new TarifaAlquilerServiceImpl(tarifaRepository, datos.calendario, mapper);
    }

    private TarifaRequest nueva(LocalDate desde) {
        return new TarifaRequest(new BigDecimal("12.00"), new BigDecimal("600.00"), desde);
    }

    @Test
    void vigenteSinFechaUsaHoyDeGuatemala() {
        when(tarifaRepository.findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(LocalDate.of(2026, 10, 23)))
                .thenReturn(Optional.of(datos.tarifa()));
        TarifaResponse esperada = new TarifaResponse(UUID.randomUUID(), BigDecimal.TEN, BigDecimal.ONE,
                LocalDate.of(2026, 1, 1), UUID.randomUUID(), null);
        when(mapper.toResponse(any(TarifaAlquiler.class))).thenReturn(esperada);
        assertSame(esperada, servicio.vigente(null));
    }

    @Test
    void sinTarifaVigenteEs404() {
        when(tarifaRepository.findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(any()))
                .thenReturn(Optional.empty());
        AlquilerException e = assertThrows(AlquilerException.class, () -> servicio.vigente(LocalDate.of(2025, 1, 1)));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        assertEquals("SIN_TARIFA", e.getCodigo());
    }

    @Test
    void registrarCreaConElAdministrador() {
        LocalDate desde = LocalDate.of(2026, 12, 1);
        when(tarifaRepository.findByVigenteDesde(desde)).thenReturn(Optional.empty());
        when(tarifaRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<TarifaAlquiler> captor = ArgumentCaptor.forClass(TarifaAlquiler.class);

        servicio.registrar(nueva(desde), datos.adminSistema);

        verify(tarifaRepository).saveAndFlush(captor.capture());
        assertEquals(desde, captor.getValue().getVigenteDesde());
        assertEquals(datos.adminSistema.id(), captor.getValue().getCreadoPor());
        assertEquals(0, new BigDecimal("12.00").compareTo(captor.getValue().getPrecioPorKm()));
    }

    @Test
    void registrarConLaMismaFechaReemplaza() {
        LocalDate desde = LocalDate.of(2026, 10, 23);
        TarifaAlquiler existente = TarifaAlquiler.builder().id(UUID.randomUUID()).vigenteDesde(desde)
                .precioPorKm(BigDecimal.ONE).precioPorDia(BigDecimal.ONE).creadoPor(UUID.randomUUID()).build();
        when(tarifaRepository.findByVigenteDesde(desde)).thenReturn(Optional.of(existente));
        when(tarifaRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<TarifaAlquiler> captor = ArgumentCaptor.forClass(TarifaAlquiler.class);

        servicio.registrar(nueva(desde), datos.adminSistema);

        verify(tarifaRepository).saveAndFlush(captor.capture());
        assertSame(existente, captor.getValue());
        assertEquals(0, new BigDecimal("600.00").compareTo(existente.getPrecioPorDia()));
    }

    @Test
    void registrarEnFechaPasadaEs400() {
        assertThrows(SolicitudInvalidaException.class,
                () -> servicio.registrar(nueva(LocalDate.of(2026, 10, 22)), datos.adminSistema));
        verify(tarifaRepository, never()).saveAndFlush(any());
    }
}
