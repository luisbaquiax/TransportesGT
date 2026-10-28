package org.transportsgt.ticketservice.services.compra.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.ticketservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.ticketservice.dto.request.VentaMostradorRequest;
import org.transportsgt.ticketservice.dto.response.CompraResponse;
import org.transportsgt.ticketservice.dto.response.Resultado;
import org.transportsgt.ticketservice.exception.NitInvalidoException;
import org.transportsgt.ticketservice.exception.SinPermisoSucursalException;
import org.transportsgt.ticketservice.fixture.ClienteRefFixture;
import org.transportsgt.ticketservice.fixture.CompraFixture;
import org.transportsgt.ticketservice.fixture.ViajeRefFixture;
import org.transportsgt.ticketservice.mappers.CompraMapperImpl;
import org.transportsgt.ticketservice.models.Compra;
import org.transportsgt.ticketservice.models.ViajeRef;
import org.transportsgt.ticketservice.repositories.ClienteRefRepository;
import org.transportsgt.ticketservice.repositories.CompraRepository;
import org.transportsgt.ticketservice.utils.Dinero;
import org.transportsgt.eventos.payload.boletos.Canal;
import org.transportsgt.eventos.payload.identidad.Rol;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VentaMostradorServiceImplTest {

    @Mock
    private CompraRepository compraRepository;
    @Mock
    private ClienteRefRepository clienteRefRepository;
    @Mock
    private ValidadorCompra validador;
    @Mock
    private GestorCompras gestor;

    private VentaMostradorServiceImpl servicio;
    private ViajeRef viaje;
    private UUID idVendedor;
    private UsuarioAutenticado cajero;
    private UUID idCompra;

    @BeforeEach
    void setUp() {
        servicio = new VentaMostradorServiceImpl(compraRepository, clienteRefRepository, validador, gestor,
                new CompraMapperImpl(), new Dinero());
        viaje = ViajeRefFixture.programado(UUID.randomUUID());
        idVendedor = UUID.randomUUID();
        cajero = ClienteRefFixture.usuario(idVendedor, Rol.CAJERO, ViajeRefFixture.SUCURSAL_XELA);
        idCompra = UUID.randomUUID();
    }

    private VentaMostradorRequest venta(String nit, String nombre) {
        return new VentaMostradorRequest(idCompra, viaje.getId(), List.of(20, 21), nit, nombre, CompraFixture.FECHA_PAGO);
    }

    private Compra vender(VentaMostradorRequest request) {
        when(compraRepository.findConBoletosById(idCompra)).thenReturn(Optional.empty());
        when(validador.validarViaje(viaje.getId())).thenReturn(viaje);
        when(validador.validarAsientos(request.asientos(), viaje)).thenReturn(List.of((short) 20, (short) 21));
        when(gestor.registrarConfirmada(any(), any(), eq(viaje.getIdRuta()))).thenAnswer(i -> i.getArgument(0));
        ArgumentCaptor<Compra> captor = ArgumentCaptor.forClass(Compra.class);
        servicio.vender(cajero, request);
        verify(gestor).registrarConfirmada(captor.capture(), eq(List.of((short) 20, (short) 21)), eq(viaje.getIdRuta()));
        return captor.getValue();
    }

    @Nested
    class Vender {

        @Test
        void vender_cuandoNoTraeNit_registraConsumidorFinal() {
            // Act
            Compra compra = vender(venta(null, null));

            // Assert
            assertAll(
                    () -> assertEquals(Canal.MOSTRADOR, compra.getCanal()),
                    () -> assertEquals("CF", compra.getNitComprador()),
                    () -> assertEquals("Consumidor final", compra.getNombreComprador()),
                    () -> assertNull(compra.getIdPago()),
                    () -> assertNull(compra.getIdUsuarioComprador()),
                    () -> assertEquals(idVendedor, compra.getIdUsuarioVendedor()),
                    () -> assertEquals(new BigDecimal("170.00"), compra.getMontoTotal()),
                    () -> assertEquals(ViajeRefFixture.SUCURSAL_XELA, compra.getIdSucursalIngreso())
            );
        }

        @Test
        void vender_cuandoTraeNitYNombre_guardaElNitNormalizado() {
            // Act
            Compra compra = vender(venta(" 1234567-k ", " Ana López "));

            // Assert
            assertAll(
                    () -> assertEquals("1234567K", compra.getNitComprador()),
                    () -> assertEquals("Ana López", compra.getNombreComprador())
            );
        }

        @Test
        void vender_cuandoTraeNitSinNombre_usaElNombreDelPerfilConEseNit() {
            // Arrange
            when(clienteRefRepository.findFirstByNit("1234567K"))
                    .thenReturn(Optional.of(ClienteRefFixture.cliente(UUID.randomUUID())));

            // Act
            Compra compra = vender(venta("1234567-K", null));

            // Assert
            assertEquals(ClienteRefFixture.NOMBRE, compra.getNombreComprador());
        }

        @Test
        void vender_cuandoElNitNoTienePerfil_usaConsumidorFinalComoNombre() {
            // Arrange
            when(clienteRefRepository.findFirstByNit("9876543")).thenReturn(Optional.empty());

            // Act
            Compra compra = vender(venta("9876543", " "));

            // Assert
            assertAll(
                    () -> assertEquals("9876543", compra.getNitComprador()),
                    () -> assertEquals("Consumidor final", compra.getNombreComprador())
            );
        }

        @Test
        void vender_cuandoElNitEsCF_registraConsumidorFinal() {
            // Act
            Compra compra = vender(venta("c-f", "Juan"));

            // Assert
            assertAll(
                    () -> assertEquals("CF", compra.getNitComprador()),
                    () -> assertEquals("Juan", compra.getNombreComprador())
            );
        }

        @Test
        void vender_cuandoElNitTieneFormatoInvalido_lanzaNitInvalido() {
            // Arrange
            when(compraRepository.findConBoletosById(idCompra)).thenReturn(Optional.empty());
            when(validador.validarViaje(viaje.getId())).thenReturn(viaje);
            when(validador.validarAsientos(any(), eq(viaje))).thenReturn(List.of((short) 20));

            // Act & Assert
            assertThrows(NitInvalidoException.class, () -> servicio.vender(cajero, venta("12.34#", null)));
            verify(gestor, never()).registrarConfirmada(any(), any(), any());
        }

        @Test
        void vender_cuandoElViajeEsDeOtraSucursal_lanzaSinPermiso() {
            // Arrange
            UsuarioAutenticado deCapital = ClienteRefFixture.usuario(idVendedor, Rol.CAJERO, ViajeRefFixture.SUCURSAL_CAPITAL);
            when(compraRepository.findConBoletosById(idCompra)).thenReturn(Optional.empty());
            when(validador.validarViaje(viaje.getId())).thenReturn(viaje);

            // Act & Assert
            assertThrows(SinPermisoSucursalException.class, () -> servicio.vender(deCapital, venta(null, null)));
            verify(gestor, never()).registrarConfirmada(any(), any(), any());
        }

        @Test
        void vender_cuandoElIdCompraSeRepite_devuelveLaVentaOriginal() {
            // Arrange
            Compra original = CompraFixture.deMostrador(idCompra, viaje, idVendedor, 20, 21);
            VentaMostradorRequest request = venta(null, null);
            when(compraRepository.findConBoletosById(idCompra)).thenReturn(Optional.of(original));

            // Act
            Resultado<CompraResponse> resultado = servicio.vender(cajero, request);

            // Assert
            assertAll(
                    () -> assertFalse(resultado.creado()),
                    () -> assertEquals(idCompra, resultado.valor().idCompra())
            );
            verify(validador).verificarRepetida(original, Canal.MOSTRADOR, idVendedor, viaje.getId(), request.asientos());
            verify(gestor, never()).registrarConfirmada(any(), any(), any());
        }

        @Test
        void vender_cuandoEsNueva_respondeCreado() {
            // Arrange
            VentaMostradorRequest request = venta(null, null);
            when(compraRepository.findConBoletosById(idCompra)).thenReturn(Optional.empty());
            when(validador.validarViaje(viaje.getId())).thenReturn(viaje);
            when(validador.validarAsientos(request.asientos(), viaje)).thenReturn(List.of((short) 20, (short) 21));
            when(gestor.registrarConfirmada(any(), any(), any()))
                    .thenReturn(CompraFixture.deMostrador(idCompra, viaje, idVendedor, 20, 21));

            // Act & Assert
            assertTrue(servicio.vender(cajero, request).creado());
        }
    }
}
