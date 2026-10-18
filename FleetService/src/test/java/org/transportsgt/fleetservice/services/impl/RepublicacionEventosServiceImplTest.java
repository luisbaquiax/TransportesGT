package org.transportsgt.fleetservice.services.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.fleetservice.dto.response.RepublicacionResponse;
import org.transportsgt.fleetservice.eventos.EventosFlota;
import org.transportsgt.fleetservice.models.Bus;
import org.transportsgt.fleetservice.models.Chofer;
import org.transportsgt.fleetservice.repositories.BusRepository;
import org.transportsgt.fleetservice.repositories.ChoferRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepublicacionEventosServiceImplTest {

    @Mock
    private BusRepository busRepository;
    @Mock
    private ChoferRepository choferRepository;
    @Mock
    private EventosFlota eventos;

    @InjectMocks
    private RepublicacionEventosServiceImpl servicio;

    @Test
    void republicaBusesYChoferes() {
        Bus bus = Bus.builder().build();
        Chofer chofer = Chofer.builder().build();
        when(busRepository.findAllByOrderByCreadoEn()).thenReturn(List.of(bus));
        when(choferRepository.findAllByOrderByCreadoEn()).thenReturn(List.of(chofer));

        RepublicacionResponse respuesta = servicio.republicar();

        assertEquals(1, respuesta.buses());
        assertEquals(1, respuesta.choferes());
        verify(eventos).busRegistrado(bus);
        verify(eventos).choferRegistrado(chofer);
    }

    @Test
    void sinRegistrosDevuelveCeros() {
        when(busRepository.findAllByOrderByCreadoEn()).thenReturn(List.of());
        when(choferRepository.findAllByOrderByCreadoEn()).thenReturn(List.of());

        RepublicacionResponse respuesta = servicio.republicar();

        assertEquals(0, respuesta.buses());
        assertEquals(0, respuesta.choferes());
        verify(eventos, times(0)).busRegistrado(org.mockito.ArgumentMatchers.any());
    }
}
