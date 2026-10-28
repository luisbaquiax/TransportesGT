package org.transportsgt.ticketservice.services.compra.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.transportsgt.ticketservice.clients.PasarelaPago;
import org.transportsgt.ticketservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.ticketservice.dto.request.CompraRequest;
import org.transportsgt.ticketservice.dto.response.ResultadoCompra;
import org.transportsgt.ticketservice.exception.CarteraNoDisponibleException;
import org.transportsgt.ticketservice.exception.CompraAnuladaException;
import org.transportsgt.ticketservice.exception.CompraDuplicadaException;
import org.transportsgt.ticketservice.exception.PagoRechazadoException;
import org.transportsgt.ticketservice.exception.ViajeNoDisponibleException;
import org.transportsgt.ticketservice.fixture.ClienteRefFixture;
import org.transportsgt.ticketservice.fixture.CompraFixture;
import org.transportsgt.ticketservice.fixture.ViajeRefFixture;
import org.transportsgt.ticketservice.mappers.CompraMapperImpl;
import org.transportsgt.ticketservice.models.Compra;
import org.transportsgt.ticketservice.models.ViajeRef;
import org.transportsgt.ticketservice.repositories.CompraRepository;
import org.transportsgt.ticketservice.utils.Dinero;
import org.transportsgt.eventos.payload.boletos.Canal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompraEnLineaServiceImplTest {

    private static final String JWT = "jwt-del-comprador";

    @Mock
    private CompraRepository compraRepository;
    @Mock
    private ValidadorCompra validador;
    @Mock
    private GestorCompras gestor;
    @Mock
    private PasarelaPago pasarela;

    private CompraEnLineaServiceImpl servicio;
    private UUID idUsuario;
    private UsuarioAutenticado comprador;
    private ViajeRef viaje;
    private UUID idCompra;
    private CompraRequest request;
    private List<Short> asientos;

    @BeforeEach
    void setUp() {
        servicio = new CompraEnLineaServiceImpl(compraRepository, validador, gestor, pasarela, new CompraMapperImpl(),
                new Dinero());
        idUsuario = UUID.randomUUID();
        comprador = ClienteRefFixture.comprador(idUsuario);
        viaje = ViajeRefFixture.programado(UUID.randomUUID());
        idCompra = UUID.randomUUID();
        request = new CompraRequest(idCompra, viaje.getId(), List.of(12, 13), CompraFixture.FECHA_PAGO);
        asientos = List.of((short) 12, (short) 13);
    }

    @Nested
    class CompraNueva {

        @Test
        void comprar_cuandoElPagoSale_reservaCobraYConfirma() {
            // Arrange
            UUID idPago = UUID.randomUUID();
            Compra reservada = CompraFixture.reservada(idCompra, viaje, idUsuario, 12, 13);
            Compra confirmada = CompraFixture.confirmada(idCompra, viaje, idUsuario, 12, 13);
            when(compraRepository.findConBoletosById(idCompra)).thenReturn(Optional.empty());
            when(validador.validarComprador(idUsuario)).thenReturn(ClienteRefFixture.cliente(idUsuario));
            when(validador.validarViaje(viaje.getId())).thenReturn(viaje);
            when(validador.validarAsientos(request.asientos(), viaje)).thenReturn(asientos);
            when(gestor.reservar(any(), eq(asientos))).thenReturn(reservada);
            when(pasarela.cobrar(eq(JWT), eq(idCompra), eq(new BigDecimal("170.00")), eq(CompraFixture.FECHA_PAGO),
                    anyString())).thenReturn(idPago);
            when(gestor.confirmar(idCompra, idPago)).thenReturn(new GestorCompras.Confirmacion(confirmada, false));
            ArgumentCaptor<Compra> captor = ArgumentCaptor.forClass(Compra.class);

            // Act
            ResultadoCompra resultado = servicio.comprar(comprador, JWT, request);

            // Assert
            verify(gestor).reservar(captor.capture(), eq(asientos));
            Compra nueva = captor.getValue();
            assertAll(
                    () -> assertEquals(ResultadoCompra.Tipo.CONFIRMADA, resultado.tipo()),
                    () -> assertEquals(Canal.EN_LINEA, nueva.getCanal()),
                    () -> assertEquals(ClienteRefFixture.NIT, nueva.getNitComprador()),
                    () -> assertEquals(ClienteRefFixture.NOMBRE, nueva.getNombreComprador()),
                    () -> assertEquals(idUsuario, nueva.getIdUsuarioComprador()),
                    () -> assertEquals(new BigDecimal("85.00"), nueva.getPrecioUnitario()),
                    () -> assertEquals(new BigDecimal("170.00"), nueva.getMontoTotal()),
                    () -> assertEquals((short) 2, nueva.getCantidadBoletos()),
                    () -> assertEquals(viaje.getIdSucursalOrigen(), nueva.getIdSucursalIngreso()),
                    () -> assertEquals(idCompra, resultado.compra().idCompra())
            );
        }

        @Test
        void comprar_cuandoCarteraRechazaElPago_liberaLosAsientosYPropagaElMotivo() {
            // Arrange
            when(compraRepository.findConBoletosById(idCompra)).thenReturn(Optional.empty());
            when(validador.validarComprador(idUsuario)).thenReturn(ClienteRefFixture.cliente(idUsuario));
            when(validador.validarViaje(viaje.getId())).thenReturn(viaje);
            when(validador.validarAsientos(request.asientos(), viaje)).thenReturn(asientos);
            when(gestor.reservar(any(), eq(asientos))).thenReturn(CompraFixture.reservada(idCompra, viaje, idUsuario, 12, 13));
            when(pasarela.cobrar(any(), any(), any(), any(), any())).thenThrow(new PagoRechazadoException(
                    HttpStatus.UNPROCESSABLE_CONTENT, "SALDO_INSUFICIENTE", "El saldo no alcanza"));

            // Act
            PagoRechazadoException ex = assertThrows(PagoRechazadoException.class,
                    () -> servicio.comprar(comprador, JWT, request));

            // Assert
            assertAll(
                    () -> assertEquals("SALDO_INSUFICIENTE", ex.getCodigo()),
                    () -> assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, ex.getStatus())
            );
            verify(gestor).liberar(idCompra, "SALDO_INSUFICIENTE");
            verify(gestor, never()).confirmar(any(), any());
        }

        @Test
        void comprar_cuandoCarteraNoResponde_dejaLaCompraReservadaYRespondePendiente() {
            // Arrange
            when(compraRepository.findConBoletosById(idCompra)).thenReturn(Optional.empty());
            when(validador.validarComprador(idUsuario)).thenReturn(ClienteRefFixture.cliente(idUsuario));
            when(validador.validarViaje(viaje.getId())).thenReturn(viaje);
            when(validador.validarAsientos(request.asientos(), viaje)).thenReturn(asientos);
            when(gestor.reservar(any(), eq(asientos))).thenReturn(CompraFixture.reservada(idCompra, viaje, idUsuario, 12, 13));
            when(pasarela.cobrar(any(), any(), any(), any(), any()))
                    .thenThrow(new CarteraNoDisponibleException("tiempo agotado"));

            // Act
            ResultadoCompra resultado = servicio.comprar(comprador, JWT, request);

            // Assert
            assertEquals(ResultadoCompra.Tipo.PENDIENTE, resultado.tipo());
            verify(gestor, never()).confirmar(any(), any());
            verify(gestor, never()).liberar(any(), any());
        }

        @Test
        void comprar_cuandoElViajeSeEliminoMientrasSeCobraba_lanzaViajeNoDisponible() {
            // Arrange
            UUID idPago = UUID.randomUUID();
            when(compraRepository.findConBoletosById(idCompra)).thenReturn(Optional.empty());
            when(validador.validarComprador(idUsuario)).thenReturn(ClienteRefFixture.cliente(idUsuario));
            when(validador.validarViaje(viaje.getId())).thenReturn(viaje);
            when(validador.validarAsientos(request.asientos(), viaje)).thenReturn(asientos);
            when(gestor.reservar(any(), eq(asientos))).thenReturn(CompraFixture.reservada(idCompra, viaje, idUsuario, 12, 13));
            when(pasarela.cobrar(any(), any(), any(), any(), any())).thenReturn(idPago);
            when(gestor.confirmar(idCompra, idPago)).thenReturn(new GestorCompras.Confirmacion(
                    CompraFixture.anulada(idCompra, viaje, idUsuario, 12, 13), true));

            // Act & Assert
            assertThrows(ViajeNoDisponibleException.class, () -> servicio.comprar(comprador, JWT, request));
        }

        @Test
        void comprar_cuandoOtraPeticionCreaLaMismaCompraALaVez_laTrataComoRepetida() {
            // Arrange
            Compra ganadora = CompraFixture.confirmada(idCompra, viaje, idUsuario, 12, 13);
            when(compraRepository.findConBoletosById(idCompra)).thenReturn(Optional.empty(), Optional.of(ganadora));
            when(validador.validarComprador(idUsuario)).thenReturn(ClienteRefFixture.cliente(idUsuario));
            when(validador.validarViaje(viaje.getId())).thenReturn(viaje);
            when(validador.validarAsientos(request.asientos(), viaje)).thenReturn(asientos);
            when(gestor.reservar(any(), eq(asientos))).thenThrow(new DataIntegrityViolationException("insert",
                    new RuntimeException("duplicate key value violates unique constraint \"compra_pkey\"")));

            // Act
            ResultadoCompra resultado = servicio.comprar(comprador, JWT, request);

            // Assert
            assertEquals(ResultadoCompra.Tipo.EXISTENTE, resultado.tipo());
            verifyNoInteractions(pasarela);
        }

        @Test
        void comprar_cuandoFallaOtraRestriccion_propagaLaExcepcion() {
            // Arrange
            when(compraRepository.findConBoletosById(idCompra)).thenReturn(Optional.empty());
            when(validador.validarComprador(idUsuario)).thenReturn(ClienteRefFixture.cliente(idUsuario));
            when(validador.validarViaje(viaje.getId())).thenReturn(viaje);
            when(validador.validarAsientos(request.asientos(), viaje)).thenReturn(asientos);
            when(gestor.reservar(any(), eq(asientos))).thenThrow(new DataIntegrityViolationException("insert",
                    new RuntimeException("ck_compra_en_linea")));

            // Act & Assert
            assertThrows(DataIntegrityViolationException.class, () -> servicio.comprar(comprador, JWT, request));
        }
    }

    @Nested
    class CompraRepetida {

        @Test
        void comprar_cuandoYaEstabaConfirmada_devuelveLaOriginalSinCobrar() {
            // Arrange
            Compra confirmada = CompraFixture.confirmada(idCompra, viaje, idUsuario, 12, 13);
            when(compraRepository.findConBoletosById(idCompra)).thenReturn(Optional.of(confirmada));

            // Act
            ResultadoCompra resultado = servicio.comprar(comprador, JWT, request);

            // Assert
            assertAll(
                    () -> assertEquals(ResultadoCompra.Tipo.EXISTENTE, resultado.tipo()),
                    () -> assertEquals(confirmada.getIdPago(), resultado.compra().idPago())
            );
            verify(validador).verificarRepetida(confirmada, Canal.EN_LINEA, idUsuario, viaje.getId(), request.asientos());
            verifyNoInteractions(pasarela);
        }

        @Test
        void comprar_cuandoSeguiaReservada_reintentaElCobroYConfirma() {
            // Arrange
            UUID idPago = UUID.randomUUID();
            Compra reservada = CompraFixture.reservada(idCompra, viaje, idUsuario, 12, 13);
            Compra confirmada = CompraFixture.confirmada(idCompra, viaje, idUsuario, 12, 13);
            when(compraRepository.findConBoletosById(idCompra)).thenReturn(Optional.of(reservada));
            when(pasarela.cobrar(eq(JWT), eq(idCompra), any(), any(), any())).thenReturn(idPago);
            when(gestor.confirmar(idCompra, idPago)).thenReturn(new GestorCompras.Confirmacion(confirmada, false));

            // Act
            ResultadoCompra resultado = servicio.comprar(comprador, JWT, request);

            // Assert
            assertEquals(ResultadoCompra.Tipo.CONFIRMADA, resultado.tipo());
            verify(gestor, never()).reservar(any(), any());
        }

        @Test
        void comprar_cuandoYaEstabaAnulada_lanzaCompraAnulada() {
            // Arrange
            when(compraRepository.findConBoletosById(idCompra))
                    .thenReturn(Optional.of(CompraFixture.anulada(idCompra, viaje, idUsuario, 12, 13)));

            // Act & Assert
            CompraAnuladaException ex = assertThrows(CompraAnuladaException.class,
                    () -> servicio.comprar(comprador, JWT, request));
            assertEquals("La compra está anulada (VIAJE_ELIMINADO)", ex.getMessage());
        }

        @Test
        void comprar_cuandoEsDeOtroUsuarioOConOtrosDatos_lanzaCompraDuplicada() {
            // Arrange
            Compra ajena = CompraFixture.confirmada(idCompra, viaje, UUID.randomUUID(), 12, 13);
            when(compraRepository.findConBoletosById(idCompra)).thenReturn(Optional.of(ajena));
            doThrow(new CompraDuplicadaException()).when(validador)
                    .verificarRepetida(ajena, Canal.EN_LINEA, idUsuario, viaje.getId(), request.asientos());

            // Act & Assert
            assertThrows(CompraDuplicadaException.class, () -> servicio.comprar(comprador, JWT, request));
            verifyNoInteractions(pasarela);
        }
    }
}
