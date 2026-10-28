package org.transportsgt.ticketservice.services.abordaje.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.payload.viajes.EstadoViaje;
import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.ticketservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.ticketservice.dto.response.AbordajeResponse;
import org.transportsgt.ticketservice.dto.response.Resultado;
import org.transportsgt.ticketservice.exception.BoletoNoEncontradoException;
import org.transportsgt.ticketservice.exception.BoletoNoValidoException;
import org.transportsgt.ticketservice.exception.SinPermisoSucursalException;
import org.transportsgt.ticketservice.exception.ViajeNoDisponibleException;
import org.transportsgt.ticketservice.exception.ViajeNoEncontradoException;
import org.transportsgt.ticketservice.fixture.ClienteRefFixture;
import org.transportsgt.ticketservice.fixture.CompraFixture;
import org.transportsgt.ticketservice.fixture.ViajeRefFixture;
import org.transportsgt.ticketservice.mappers.AbordajeMapperImpl;
import org.transportsgt.ticketservice.models.Boleto;
import org.transportsgt.ticketservice.models.ViajeRef;
import org.transportsgt.ticketservice.repositories.BoletoRepository;
import org.transportsgt.ticketservice.repositories.ViajeRefRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class AbordajeServiceImplTest {

    private static final OffsetDateTime AHORA = OffsetDateTime.ofInstant(ViajeRefFixture.AHORA, ZoneOffset.UTC);

    @Mock
    private BoletoRepository boletoRepository;
    @Mock
    private ViajeRefRepository viajeRefRepository;

    private AbordajeServiceImpl servicio;
    private ViajeRef viaje;
    private Boleto boleto;
    private UsuarioAutenticado cajero;

    @BeforeEach
    void setUp() {
        servicio = new AbordajeServiceImpl(boletoRepository, new ValidadorAbordaje(viajeRefRepository),
                new AbordajeMapperImpl(), Clock.fixed(ViajeRefFixture.AHORA, ZoneOffset.UTC));
        viaje = ViajeRefFixture.programado(UUID.randomUUID());
        boleto = CompraFixture.confirmada(UUID.randomUUID(), viaje, UUID.randomUUID(), 7).getBoletos().getFirst();
        cajero = ClienteRefFixture.usuario(UUID.randomUUID(), Rol.CAJERO, ViajeRefFixture.SUCURSAL_XELA);
    }

    private void existen() {
        when(viajeRefRepository.findById(viaje.getId())).thenReturn(Optional.of(viaje));
        when(boletoRepository.findByCodigoParaAbordar(boleto.getCodigo())).thenReturn(Optional.of(boleto));
    }

    @Test
    void registrar_boletoConfirmado_guardaHoraYUsuario() {
        // Arrange
        existen();

        // Act
        Resultado<AbordajeResponse> resultado = servicio.registrar(cajero, viaje.getId(), "  bol-7 ");

        // Assert
        assertTrue(resultado.creado());
        assertEquals(AHORA, boleto.getAbordadoEn());
        assertEquals(cajero.id(), boleto.getAbordadoPor());
        assertEquals((short) 7, resultado.valor().numeroAsiento());
        assertEquals(ClienteRefFixture.NOMBRE, resultado.valor().nombreComprador());
    }

    @Test
    void registrar_yaAbordado_devuelveElOriginalSinCambiarlo() {
        // Arrange
        UUID primero = UUID.randomUUID();
        OffsetDateTime antes = AHORA.minusMinutes(20);
        boleto.setAbordadoEn(antes);
        boleto.setAbordadoPor(primero);
        viaje.setEstado(EstadoViaje.EN_TRANSITO);
        existen();

        // Act
        Resultado<AbordajeResponse> resultado = servicio.registrar(cajero, viaje.getId(), boleto.getCodigo());

        // Assert
        assertFalse(resultado.creado());
        assertEquals(antes, resultado.valor().abordadoEn());
        assertEquals(primero, boleto.getAbordadoPor());
    }

    @Test
    void registrar_viajeQueYaSalio_responde409() {
        // Arrange
        viaje.setEstado(EstadoViaje.EN_TRANSITO);
        existen();

        // Act & Assert
        assertThrows(ViajeNoDisponibleException.class,
                () -> servicio.registrar(cajero, viaje.getId(), boleto.getCodigo()));
        assertFalse(boleto.abordado());
    }

    @Test
    void registrar_boletoDeOtroViaje_responde409() {
        // Arrange
        Boleto ajeno = CompraFixture.confirmada(UUID.randomUUID(), ViajeRefFixture.programado(UUID.randomUUID()),
                UUID.randomUUID(), 3).getBoletos().getFirst();
        when(viajeRefRepository.findById(viaje.getId())).thenReturn(Optional.of(viaje));
        when(boletoRepository.findByCodigoParaAbordar(ajeno.getCodigo())).thenReturn(Optional.of(ajeno));

        // Act & Assert
        assertThrows(BoletoNoValidoException.class, () -> servicio.registrar(cajero, viaje.getId(), ajeno.getCodigo()));
    }

    @Test
    void registrar_boletoAnulado_responde409() {
        // Arrange
        boleto = CompraFixture.anulada(UUID.randomUUID(), viaje, UUID.randomUUID(), 9).getBoletos().getFirst();
        existen();

        // Act & Assert
        assertThrows(BoletoNoValidoException.class,
                () -> servicio.registrar(cajero, viaje.getId(), boleto.getCodigo()));
    }

    @Test
    void registrar_codigoInexistente_responde404() {
        // Arrange
        when(viajeRefRepository.findById(viaje.getId())).thenReturn(Optional.of(viaje));
        when(boletoRepository.findByCodigoParaAbordar(anyString())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(BoletoNoEncontradoException.class, () -> servicio.registrar(cajero, viaje.getId(), "BOL-NADA"));
    }

    @Test
    void registrar_cajeroDeOtraSucursal_responde403SinBuscarElBoleto() {
        // Arrange
        UsuarioAutenticado ajeno = ClienteRefFixture.usuario(UUID.randomUUID(), Rol.CAJERO,
                ViajeRefFixture.SUCURSAL_CAPITAL);
        when(viajeRefRepository.findById(viaje.getId())).thenReturn(Optional.of(viaje));

        // Act & Assert
        assertThrows(SinPermisoSucursalException.class,
                () -> servicio.registrar(ajeno, viaje.getId(), boleto.getCodigo()));
        verify(boletoRepository, never()).findByCodigoParaAbordar(anyString());
    }

    @Test
    void registrar_viajeEliminado_responde404() {
        // Arrange
        viaje.setEliminado(true);
        when(viajeRefRepository.findById(viaje.getId())).thenReturn(Optional.of(viaje));

        // Act & Assert
        assertThrows(ViajeNoEncontradoException.class,
                () -> servicio.registrar(cajero, viaje.getId(), boleto.getCodigo()));
    }
}
