package org.transportsgt.ticketservice.services.abordaje.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.payload.Canal;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.ticketservice.dto.response.PasajerosViajeResponse;
import org.transportsgt.ticketservice.fixture.ClienteRefFixture;
import org.transportsgt.ticketservice.fixture.CompraFixture;
import org.transportsgt.ticketservice.fixture.ViajeRefFixture;
import org.transportsgt.ticketservice.mappers.AbordajeMapperImpl;
import org.transportsgt.ticketservice.models.Boleto;
import org.transportsgt.ticketservice.models.Compra;
import org.transportsgt.ticketservice.models.ViajeRef;
import org.transportsgt.ticketservice.repositories.BoletoRepository;
import org.transportsgt.ticketservice.repositories.ViajeRefRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasajerosViajeServiceImplTest {

    @Mock
    private BoletoRepository boletoRepository;
    @Mock
    private ViajeRefRepository viajeRefRepository;

    @Test
    void pasajeros_cuentaLosAbordadosYMapeaLaCompra() {
        // Arrange
        PasajerosViajeServiceImpl servicio = new PasajerosViajeServiceImpl(boletoRepository,
                new ValidadorAbordaje(viajeRefRepository), new AbordajeMapperImpl());
        ViajeRef viaje = ViajeRefFixture.programado(UUID.randomUUID());
        Compra compra = CompraFixture.deMostrador(UUID.randomUUID(), viaje, UUID.randomUUID(), 1, 2);
        List<Boleto> boletos = compra.getBoletos();
        boletos.getFirst().setAbordadoEn(OffsetDateTime.now());
        boletos.getFirst().setAbordadoPor(UUID.randomUUID());
        when(viajeRefRepository.findById(viaje.getId())).thenReturn(Optional.of(viaje));
        when(boletoRepository.findConfirmadosDelViaje(viaje.getId())).thenReturn(boletos);

        // Act
        PasajerosViajeResponse respuesta = servicio.pasajeros(
                ClienteRefFixture.usuario(UUID.randomUUID(), Rol.ADMIN_SUCURSAL, ViajeRefFixture.SUCURSAL_XELA),
                viaje.getId());

        // Assert
        assertEquals(2, respuesta.boletos());
        assertEquals(1, respuesta.abordados());
        assertEquals("P-123ABC", respuesta.placaBus());
        assertEquals(compra.getId(), respuesta.pasajeros().getFirst().idCompra());
        assertEquals(Canal.MOSTRADOR, respuesta.pasajeros().getFirst().canal());
        assertNull(respuesta.pasajeros().get(1).abordadoEn());
    }
}
