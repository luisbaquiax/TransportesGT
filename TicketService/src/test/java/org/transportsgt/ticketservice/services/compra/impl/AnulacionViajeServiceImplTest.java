package org.transportsgt.ticketservice.services.compra.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.ticketservice.models.EstadoCompra;
import org.transportsgt.ticketservice.repositories.CompraRepository;
import org.transportsgt.ticketservice.repositories.ViajeRefRepository;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnulacionViajeServiceImplTest {

    @Mock
    private ViajeRefRepository viajeRefRepository;
    @Mock
    private CompraRepository compraRepository;
    @Mock
    private GestorCompras gestor;

    private AnulacionViajeServiceImpl servicio;

    @BeforeEach
    void setUp() {
        servicio = new AnulacionViajeServiceImpl(viajeRefRepository, compraRepository, gestor);
    }

    @Test
    void anularViaje_marcaElViajeYAnulaLasComprasActivas() {
        // Arrange
        UUID idViaje = UUID.randomUUID();
        UUID reservada = UUID.randomUUID();
        UUID confirmada = UUID.randomUUID();
        UUID yaAnulada = UUID.randomUUID();
        when(compraRepository.findIdsPorViajeYEstado(idViaje, EnumSet.of(EstadoCompra.RESERVADA, EstadoCompra.CONFIRMADA)))
                .thenReturn(List.of(reservada, confirmada, yaAnulada));
        when(gestor.anularPorViaje(reservada)).thenReturn(true);
        when(gestor.anularPorViaje(confirmada)).thenReturn(true);
        when(gestor.anularPorViaje(yaAnulada)).thenReturn(false);

        // Act
        int anuladas = servicio.anularViaje(idViaje);

        // Assert
        assertEquals(2, anuladas);
        InOrder orden = inOrder(viajeRefRepository, compraRepository);
        orden.verify(viajeRefRepository).marcarEliminado(idViaje);
        orden.verify(compraRepository).findIdsPorViajeYEstado(org.mockito.ArgumentMatchers.eq(idViaje),
                org.mockito.ArgumentMatchers.any());
    }
}
