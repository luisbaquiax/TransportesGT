package org.transportsgt.ticketservice.services.consulta.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.ticketservice.config.security.UsuarioAutenticado;
import org.transportsgt.ticketservice.documentos.BoletosPdf;
import org.transportsgt.ticketservice.exception.CompraNoConfirmadaException;
import org.transportsgt.ticketservice.exception.CompraNoEncontradaException;
import org.transportsgt.ticketservice.fixture.ClienteRefFixture;
import org.transportsgt.ticketservice.fixture.CompraFixture;
import org.transportsgt.ticketservice.fixture.ViajeRefFixture;
import org.transportsgt.ticketservice.models.Boleto;
import org.transportsgt.ticketservice.models.Compra;
import org.transportsgt.ticketservice.models.EstadoBoleto;
import org.transportsgt.ticketservice.models.ViajeRef;
import org.transportsgt.ticketservice.repositories.CompraRepository;
import org.transportsgt.ticketservice.repositories.ViajeRefRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoletosPdfServiceImplTest {

    @Mock
    private CompraRepository compraRepository;
    @Mock
    private ViajeRefRepository viajeRefRepository;
    @Mock
    private ReglasVisibilidad visibilidad;
    @Mock
    private BoletosPdf boletosPdf;

    private BoletosPdfServiceImpl servicio;
    private ViajeRef viaje;
    private UsuarioAutenticado comprador;

    @BeforeEach
    void setUp() {
        servicio = new BoletosPdfServiceImpl(compraRepository, viajeRefRepository, visibilidad, boletosPdf);
        viaje = ViajeRefFixture.programado(UUID.randomUUID());
        comprador = ClienteRefFixture.comprador(UUID.randomUUID());
    }

    @Test
    void generar_compraConfirmada_soloIncluyeLosBoletosConfirmadosPorAsiento() {
        // Arrange
        Compra compra = CompraFixture.confirmada(UUID.randomUUID(), viaje, comprador.id(), 9, 3, 5);
        compra.getBoletos().get(2).setEstado(EstadoBoleto.ANULADO);
        when(compraRepository.findConBoletosById(compra.getId())).thenReturn(Optional.of(compra));
        when(visibilidad.puedeVer(comprador, compra)).thenReturn(true);
        when(viajeRefRepository.findById(viaje.getId())).thenReturn(Optional.of(viaje));
        byte[] pdf = {1, 2, 3};
        when(boletosPdf.generar(eq(compra), eq(viaje), any())).thenReturn(pdf);

        // Act
        byte[] resultado = servicio.generar(comprador, compra.getId());

        // Assert
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Boleto>> boletos = ArgumentCaptor.forClass(List.class);
        verify(boletosPdf).generar(eq(compra), eq(viaje), boletos.capture());
        assertEquals(List.of((short) 3, (short) 9), boletos.getValue().stream().map(Boleto::getNumeroAsiento).toList());
        assertArrayEquals(pdf, resultado);
    }

    @Test
    void generar_compraAjena_responde404() {
        // Arrange
        Compra compra = CompraFixture.confirmada(UUID.randomUUID(), viaje, UUID.randomUUID(), 1);
        when(compraRepository.findConBoletosById(compra.getId())).thenReturn(Optional.of(compra));
        when(visibilidad.puedeVer(comprador, compra)).thenReturn(false);

        // Act & Assert
        assertThrows(CompraNoEncontradaException.class, () -> servicio.generar(comprador, compra.getId()));
        verify(boletosPdf, never()).generar(any(), any(), any());
    }

    @Test
    void generar_compraAnulada_responde409() {
        // Arrange
        Compra compra = CompraFixture.anulada(UUID.randomUUID(), viaje, comprador.id(), 1);
        when(compraRepository.findConBoletosById(compra.getId())).thenReturn(Optional.of(compra));
        when(visibilidad.puedeVer(comprador, compra)).thenReturn(true);

        // Act & Assert
        assertThrows(CompraNoConfirmadaException.class, () -> servicio.generar(comprador, compra.getId()));
    }
}
