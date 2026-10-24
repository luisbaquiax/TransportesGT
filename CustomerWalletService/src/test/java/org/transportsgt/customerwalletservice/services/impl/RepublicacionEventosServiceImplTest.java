package org.transportsgt.customerwalletservice.services.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.customerwalletservice.dto.response.RepublicacionResponse;
import org.transportsgt.customerwalletservice.eventos.EventosCartera;
import org.transportsgt.customerwalletservice.fixture.ClienteFixture;
import org.transportsgt.customerwalletservice.models.Cliente;
import org.transportsgt.customerwalletservice.repositories.ClienteRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepublicacionEventosServiceImplTest {

    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private EventosCartera eventos;

    @InjectMocks
    private RepublicacionEventosServiceImpl servicio;

    @Test
    void republicar_conClientes_publicaElPerfilDeCadaUno() {
        // Arrange
        Cliente primero = ClienteFixture.cliente(UUID.randomUUID());
        Cliente segundo = ClienteFixture.sinNit(UUID.randomUUID());
        when(clienteRepository.findAllByOrderByCreadoEn()).thenReturn(List.of(primero, segundo));

        // Act
        RepublicacionResponse respuesta = servicio.republicar();

        // Assert
        assertEquals(2, respuesta.clientes());
        verify(eventos).perfilClienteActualizado(primero);
        verify(eventos).perfilClienteActualizado(segundo);
    }

    @Test
    void republicar_sinClientes_devuelveCero() {
        // Arrange
        when(clienteRepository.findAllByOrderByCreadoEn()).thenReturn(List.of());

        // Act
        RepublicacionResponse respuesta = servicio.republicar();

        // Assert
        assertEquals(0, respuesta.clientes());
        verify(eventos, never()).perfilClienteActualizado(any());
    }
}
