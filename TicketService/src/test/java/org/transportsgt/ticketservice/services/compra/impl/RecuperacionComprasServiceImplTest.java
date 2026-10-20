package org.transportsgt.ticketservice.services.compra.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.ticketservice.clients.PasarelaPago;
import org.transportsgt.ticketservice.config.BoletosProperties;
import org.transportsgt.ticketservice.exception.CarteraNoDisponibleException;
import org.transportsgt.ticketservice.fixture.CompraFixture;
import org.transportsgt.ticketservice.fixture.ViajeRefFixture;
import org.transportsgt.ticketservice.models.Compra;
import org.transportsgt.ticketservice.repositories.CompraRepository;
import org.transportsgt.ticketservice.services.compra.RecuperacionComprasService.Resultado;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecuperacionComprasServiceImplTest {

    @Mock
    private CompraRepository compraRepository;
    @Mock
    private PasarelaPago pasarelaPago;
    @Mock
    private GestorCompras gestor;

    private RecuperacionComprasServiceImpl servicio;
    private Compra compra;

    @BeforeEach
    void setUp() {
        BoletosProperties propiedades = new BoletosProperties(new BoletosProperties.Compra(10),
                new BoletosProperties.Reserva(Duration.ofMinutes(10)),
                new BoletosProperties.Recuperacion(Duration.ofMinutes(2), 60000));
        servicio = new RecuperacionComprasServiceImpl(compraRepository, pasarelaPago, gestor, propiedades,
                Clock.fixed(ViajeRefFixture.AHORA, ZoneOffset.UTC));
        compra = CompraFixture.reservada(UUID.randomUUID(), ViajeRefFixture.programado(UUID.randomUUID()),
                UUID.randomUUID(), 5);
    }

    @Test
    void pendientes_buscaLasReservasQueVencenAntesDelMargen() {
        // Arrange
        OffsetDateTime limite = OffsetDateTime.ofInstant(ViajeRefFixture.AHORA, ZoneOffset.UTC).plusMinutes(8);
        when(compraRepository.findIdsReservadasQueVencenAntesDe(limite)).thenReturn(List.of(compra.getId()));

        // Act & Assert
        assertEquals(List.of(compra.getId()), servicio.pendientes());
    }

    @Nested
    class Recuperar {

        @Test
        void recuperar_cuandoCarteraTieneElPago_confirma() {
            // Arrange
            UUID idPago = UUID.randomUUID();
            when(compraRepository.findById(compra.getId())).thenReturn(Optional.of(compra));
            when(pasarelaPago.consultarPago(compra.getId())).thenReturn(Optional.of(idPago));

            // Act & Assert
            assertEquals(Resultado.CONFIRMADA, servicio.recuperar(compra.getId()));
            verify(gestor).confirmar(compra.getId(), idPago);
        }

        @Test
        void recuperar_cuandoNoHayPagoYLaReservaVencio_libera() {
            // Arrange
            compra.setReservaExpiraEn(OffsetDateTime.ofInstant(ViajeRefFixture.AHORA, ZoneOffset.UTC));
            when(compraRepository.findById(compra.getId())).thenReturn(Optional.of(compra));
            when(pasarelaPago.consultarPago(compra.getId())).thenReturn(Optional.empty());

            // Act & Assert
            assertEquals(Resultado.LIBERADA, servicio.recuperar(compra.getId()));
            verify(gestor).liberar(compra.getId(), GestorCompras.MOTIVO_RESERVA_VENCIDA);
        }

        @Test
        void recuperar_cuandoNoHayPagoYLaReservaSigueVigente_quedaPendiente() {
            // Arrange
            when(compraRepository.findById(compra.getId())).thenReturn(Optional.of(compra));
            when(pasarelaPago.consultarPago(compra.getId())).thenReturn(Optional.empty());

            // Act & Assert
            assertEquals(Resultado.PENDIENTE, servicio.recuperar(compra.getId()));
            verify(gestor, never()).liberar(any(), any());
        }

        @Test
        void recuperar_cuandoCarteraNoResponde_quedaPendiente() {
            // Arrange
            when(compraRepository.findById(compra.getId())).thenReturn(Optional.of(compra));
            when(pasarelaPago.consultarPago(compra.getId())).thenThrow(new CarteraNoDisponibleException("timeout"));

            // Act & Assert
            assertEquals(Resultado.PENDIENTE, servicio.recuperar(compra.getId()));
            verifyNoInteractions(gestor);
        }

        @Test
        void recuperar_cuandoYaNoEstaReservadaONoExiste_noHaceNada() {
            // Arrange
            Compra confirmada = CompraFixture.confirmada(UUID.randomUUID(), ViajeRefFixture.programado(UUID.randomUUID()),
                    UUID.randomUUID(), 1);
            UUID inexistente = UUID.randomUUID();
            when(compraRepository.findById(confirmada.getId())).thenReturn(Optional.of(confirmada));
            when(compraRepository.findById(inexistente)).thenReturn(Optional.empty());

            // Act & Assert
            assertEquals(Resultado.PENDIENTE, servicio.recuperar(confirmada.getId()));
            assertEquals(Resultado.PENDIENTE, servicio.recuperar(inexistente));
            verifyNoInteractions(pasarelaPago, gestor);
        }
    }
}
