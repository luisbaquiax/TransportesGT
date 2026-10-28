package org.transportsgt.tripservice.services.admin.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.transportsgt.eventos.payload.flota.BusAsignado;
import org.transportsgt.eventos.payload.flota.ChoferAsignado;
import org.transportsgt.tripservice.dto.response.Responses;
import org.transportsgt.tripservice.eventos.EventosViaje;
import org.transportsgt.tripservice.eventos.RecursosEvento;
import org.transportsgt.tripservice.models.Ruta;
import org.transportsgt.tripservice.models.Viaje;
import org.transportsgt.tripservice.repositories.RutaRepository;
import org.transportsgt.tripservice.repositories.ViajeRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepublicacionEventosServiceImplTest {

    @Mock
    private RutaRepository rutaRepository;
    @Mock
    private ViajeRepository viajeRepository;
    @Mock
    private RecursosEvento recursos;
    @Mock
    private EventosViaje eventos;

    @InjectMocks
    private RepublicacionEventosServiceImpl servicio;

    @Test
    void republicar_publicaRutasAntesQueViajesConSusRecursos() {
        // Arrange
        Ruta ruta = Ruta.builder().id(UUID.randomUUID()).build();
        UUID idBus = UUID.randomUUID();
        UUID idChofer = UUID.randomUUID();
        Viaje asignado = Viaje.builder().id(UUID.randomUUID()).idBus(idBus).idChofer(idChofer).build();
        Viaje pendiente = Viaje.builder().id(UUID.randomUUID()).build();
        BusAsignado bus = new BusAsignado(idBus, "P123ABC", 40);
        ChoferAsignado chofer = new ChoferAsignado(idChofer, "Ana López");
        when(rutaRepository.findAll(Sort.by("creadoEn"))).thenReturn(List.of(ruta));
        when(viajeRepository.findAll(Sort.by("fechaHoraSalida"))).thenReturn(List.of(asignado, pendiente));
        when(recursos.buses(Set.of(idBus))).thenReturn(Map.of(idBus, bus));
        when(recursos.choferes(Set.of(idChofer))).thenReturn(Map.of(idChofer, chofer));

        // Act
        Responses.RepublicacionResponse respuesta = servicio.republicar();

        // Assert
        assertEquals(new Responses.RepublicacionResponse(1, 2), respuesta);
        var orden = inOrder(eventos);
        orden.verify(eventos).rutaCreada(ruta);
        orden.verify(eventos).viajeProgramado(asignado, bus, chofer);
        verify(eventos).viajeProgramado(pendiente, null, null);
    }
}
