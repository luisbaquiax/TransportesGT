package org.transportsgt.ticketservice.jobs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.ticketservice.services.RecuperacionComprasService;
import org.transportsgt.ticketservice.services.RecuperacionComprasService.Resultado;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class RecuperacionComprasJobTest {

    @Mock
    private RecuperacionComprasService recuperacion;

    @InjectMocks
    private RecuperacionComprasJob job;

    @Test
    void ejecutar_cuandoUnaCompraFalla_sigueConLasDemas() {
        // Arrange
        UUID falla = UUID.randomUUID();
        UUID confirmada = UUID.randomUUID();
        UUID liberada = UUID.randomUUID();
        when(recuperacion.pendientes()).thenReturn(List.of(falla, confirmada, liberada));
        when(recuperacion.recuperar(falla)).thenThrow(new IllegalStateException("bloqueo"));
        when(recuperacion.recuperar(confirmada)).thenReturn(Resultado.CONFIRMADA);
        when(recuperacion.recuperar(liberada)).thenReturn(Resultado.LIBERADA);

        // Act
        job.ejecutar();

        // Assert
        verify(recuperacion).recuperar(confirmada);
        verify(recuperacion).recuperar(liberada);
    }

    @Test
    void ejecutar_sinPendientes_noRecuperaNada() {
        // Arrange
        when(recuperacion.pendientes()).thenReturn(List.of());

        // Act
        job.ejecutar();

        // Assert
        verify(recuperacion, never()).recuperar(any());
    }
}
