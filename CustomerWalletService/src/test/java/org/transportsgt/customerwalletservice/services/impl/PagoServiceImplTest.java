package org.transportsgt.customerwalletservice.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.customerwalletservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.customerwalletservice.dto.request.PagoRequest;
import org.transportsgt.customerwalletservice.dto.response.PagoConsultaResponse;
import org.transportsgt.customerwalletservice.dto.response.PagoResponse;
import org.transportsgt.customerwalletservice.dto.response.Resultado;
import org.transportsgt.customerwalletservice.exception.ClaveIdempotenciaInvalidaException;
import org.transportsgt.customerwalletservice.exception.ClaveIdempotenciaRequeridaException;
import org.transportsgt.customerwalletservice.exception.ClaveIdempotenciaReutilizadaException;
import org.transportsgt.customerwalletservice.exception.ClienteNoEncontradoException;
import org.transportsgt.customerwalletservice.exception.CuentaInactivaException;
import org.transportsgt.customerwalletservice.exception.PagoNoEncontradoException;
import org.transportsgt.customerwalletservice.exception.PerfilIncompletoException;
import org.transportsgt.customerwalletservice.exception.SaldoInsuficienteException;
import org.transportsgt.customerwalletservice.fixture.CarteraFixture;
import org.transportsgt.customerwalletservice.fixture.ClienteFixture;
import org.transportsgt.customerwalletservice.mappers.CarteraMapperImpl;
import org.transportsgt.customerwalletservice.models.Cartera;
import org.transportsgt.customerwalletservice.models.Cliente;
import org.transportsgt.customerwalletservice.models.MovimientoCartera;
import org.transportsgt.customerwalletservice.models.TipoMovimiento;
import org.transportsgt.customerwalletservice.repositories.CarteraRepository;
import org.transportsgt.customerwalletservice.repositories.MovimientoCarteraRepository;
import org.transportsgt.customerwalletservice.utils.Dinero;
import org.transportsgt.eventos.payload.costos.TipoReferencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PagoServiceImplTest {

    private static final LocalDate FECHA = LocalDate.parse("2026-10-20");

    @Mock
    private CarteraRepository carteraRepository;
    @Mock
    private MovimientoCarteraRepository movimientoRepository;

    private PagoServiceImpl servicio;
    private UUID idUsuario;
    private Cliente cliente;
    private Cartera cartera;
    private UsuarioAutenticado usuario;
    private String clave;
    private UUID idReferencia;

    @BeforeEach
    void setUp() {
        servicio = new PagoServiceImpl(carteraRepository, movimientoRepository, new CarteraMapperImpl(),
                new ReglasCartera(new Dinero()), new Dinero());
        idUsuario = UUID.randomUUID();
        cliente = ClienteFixture.cliente(idUsuario);
        cartera = CarteraFixture.cartera(cliente, "150.00");
        usuario = ClienteFixture.usuario(idUsuario);
        clave = UUID.randomUUID().toString();
        idReferencia = UUID.randomUUID();
    }

    private PagoRequest pago(String monto) {
        return new PagoRequest(TipoReferencia.BOLETOS, idReferencia, new BigDecimal(monto), FECHA, "2 boletos");
    }

    @Nested
    class Pagar {

        @Test
        void pagar_cuandoAlcanzaElSaldo_descuentaYRespondeCreado() {
            // Arrange
            when(carteraRepository.findByIdUsuarioParaActualizar(idUsuario)).thenReturn(Optional.of(cartera));
            when(movimientoRepository.findByTipoAndTipoReferenciaAndIdReferencia(
                    TipoMovimiento.PAGO, TipoReferencia.BOLETOS, idReferencia)).thenReturn(Optional.empty());
            when(movimientoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(carteraRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
            ArgumentCaptor<MovimientoCartera> captor = ArgumentCaptor.forClass(MovimientoCartera.class);

            // Act
            Resultado<PagoResponse> resultado = servicio.pagar(usuario, idReferencia.toString(), pago("150"));

            // Assert
            verify(movimientoRepository).save(captor.capture());
            MovimientoCartera pago = captor.getValue();
            assertAll(
                    () -> assertTrue(resultado.creado()),
                    () -> assertEquals(TipoMovimiento.PAGO, pago.getTipo()),
                    () -> assertEquals(new BigDecimal("150.00"), pago.getMonto()),
                    () -> assertEquals(new BigDecimal("0.00"), pago.getSaldoPosterior()),
                    () -> assertEquals(TipoReferencia.BOLETOS, pago.getTipoReferencia()),
                    () -> assertEquals(idReferencia, pago.getIdReferencia()),
                    () -> assertEquals(idReferencia.toString(), pago.getClaveIdempotencia()),
                    () -> assertEquals("2 boletos", pago.getDescripcion()),
                    () -> assertEquals(new BigDecimal("0.00"), cartera.getSaldo()),
                    () -> assertEquals(new BigDecimal("0.00"), resultado.valor().saldoPosterior())
            );
        }

        @Test
        void pagar_cuandoNoAlcanzaElSaldo_lanzaSaldoInsuficiente() {
            // Arrange
            when(carteraRepository.findByIdUsuarioParaActualizar(idUsuario)).thenReturn(Optional.of(cartera));
            when(movimientoRepository.findByTipoAndTipoReferenciaAndIdReferencia(
                    TipoMovimiento.PAGO, TipoReferencia.BOLETOS, idReferencia)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(SaldoInsuficienteException.class,
                    () -> servicio.pagar(usuario, idReferencia.toString(), pago("150.01")));
            verify(movimientoRepository, never()).save(any());
            assertEquals(new BigDecimal("150.00"), cartera.getSaldo());
        }

        @Test
        void pagar_cuandoElPagoYaExisteConElMismoMonto_devuelveElOriginal() {
            // Arrange
            MovimientoCartera original = CarteraFixture.pago(cartera, TipoReferencia.BOLETOS, idReferencia, "100.00");
            when(carteraRepository.findByIdUsuarioParaActualizar(idUsuario)).thenReturn(Optional.of(cartera));
            when(movimientoRepository.findByTipoAndTipoReferenciaAndIdReferencia(
                    TipoMovimiento.PAGO, TipoReferencia.BOLETOS, idReferencia)).thenReturn(Optional.of(original));

            // Act
            Resultado<PagoResponse> resultado = servicio.pagar(usuario, idReferencia.toString(), pago("100"));

            // Assert
            assertAll(
                    () -> assertFalse(resultado.creado()),
                    () -> assertEquals(original.getId(), resultado.valor().idPago())
            );
            verify(movimientoRepository, never()).save(any());
        }

        @Test
        void pagar_cuandoElPagoYaExisteConOtroMonto_lanzaClaveReutilizada() {
            // Arrange
            when(carteraRepository.findByIdUsuarioParaActualizar(idUsuario)).thenReturn(Optional.of(cartera));
            when(movimientoRepository.findByTipoAndTipoReferenciaAndIdReferencia(
                    TipoMovimiento.PAGO, TipoReferencia.BOLETOS, idReferencia))
                    .thenReturn(Optional.of(CarteraFixture.pago(cartera, TipoReferencia.BOLETOS, idReferencia, "100.00")));

            // Act & Assert
            assertThrows(ClaveIdempotenciaReutilizadaException.class,
                    () -> servicio.pagar(usuario, idReferencia.toString(), pago("90.00")));
        }

        @Test
        void pagar_cuandoLaClaveNoEsLaReferencia_lanzaClaveInvalida() {
            // Act & Assert
            assertThrows(ClaveIdempotenciaInvalidaException.class, () -> servicio.pagar(usuario, clave, pago("10")));
            verifyNoInteractions(carteraRepository, movimientoRepository);
        }

        @Test
        void pagar_cuandoFaltaLaClave_lanzaClaveRequerida() {
            // Act & Assert
            assertThrows(ClaveIdempotenciaRequeridaException.class, () -> servicio.pagar(usuario, null, pago("10")));
        }

        @Test
        void pagar_cuandoLaCuentaEstaInactiva_lanzaCuentaInactiva() {
            // Arrange
            Cartera deInactivo = CarteraFixture.cartera(ClienteFixture.inactivo(idUsuario), "500.00");
            when(carteraRepository.findByIdUsuarioParaActualizar(idUsuario)).thenReturn(Optional.of(deInactivo));

            // Act & Assert
            assertThrows(CuentaInactivaException.class,
                    () -> servicio.pagar(usuario, idReferencia.toString(), pago("10")));
            verifyNoInteractions(movimientoRepository);
        }

        @Test
        void pagar_cuandoElPerfilNoTieneNit_lanzaPerfilIncompleto() {
            // Arrange
            Cartera sinNit = CarteraFixture.cartera(ClienteFixture.sinNit(idUsuario), "500.00");
            when(carteraRepository.findByIdUsuarioParaActualizar(idUsuario)).thenReturn(Optional.of(sinNit));

            // Act & Assert
            assertThrows(PerfilIncompletoException.class,
                    () -> servicio.pagar(usuario, idReferencia.toString(), pago("10")));
            verifyNoInteractions(movimientoRepository);
        }

        @Test
        void pagar_cuandoNoHayCliente_lanzaClienteNoEncontrado() {
            // Arrange
            when(carteraRepository.findByIdUsuarioParaActualizar(idUsuario)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ClienteNoEncontradoException.class,
                    () -> servicio.pagar(usuario, idReferencia.toString(), pago("10")));
            verifyNoInteractions(movimientoRepository);
        }
    }

    @Nested
    class ConsultarPago {

        @Test
        void consultarPago_cuandoEsDelUsuario_devuelveElPago() {
            // Arrange
            MovimientoCartera pago = CarteraFixture.pago(cartera, TipoReferencia.ALQUILER, idReferencia, "3800.00");
            when(movimientoRepository.findByTipoAndTipoReferenciaAndIdReferencia(
                    TipoMovimiento.PAGO, TipoReferencia.ALQUILER, idReferencia)).thenReturn(Optional.of(pago));

            // Act
            PagoConsultaResponse respuesta = servicio.consultarPago(idUsuario, TipoReferencia.ALQUILER, idReferencia);

            // Assert
            assertAll(
                    () -> assertEquals(pago.getId(), respuesta.idPago()),
                    () -> assertEquals(new BigDecimal("3800.00"), respuesta.monto()),
                    () -> assertEquals(CarteraFixture.FECHA, respuesta.fechaPago())
            );
        }

        @Test
        void consultarPago_cuandoEsInterno_devuelveCualquierPago() {
            // Arrange
            MovimientoCartera pago = CarteraFixture.pago(cartera, TipoReferencia.BOLETOS, idReferencia, "170.00");
            when(movimientoRepository.findByTipoAndTipoReferenciaAndIdReferencia(
                    TipoMovimiento.PAGO, TipoReferencia.BOLETOS, idReferencia)).thenReturn(Optional.of(pago));

            // Act
            PagoConsultaResponse respuesta = servicio.consultarPago(null, TipoReferencia.BOLETOS, idReferencia);

            // Assert
            assertEquals(pago.getId(), respuesta.idPago());
        }

        @Test
        void consultarPago_cuandoEsDeOtroUsuario_lanzaPagoNoEncontrado() {
            // Arrange
            when(movimientoRepository.findByTipoAndTipoReferenciaAndIdReferencia(
                    TipoMovimiento.PAGO, TipoReferencia.BOLETOS, idReferencia))
                    .thenReturn(Optional.of(CarteraFixture.pago(cartera, TipoReferencia.BOLETOS, idReferencia, "1.00")));

            // Act & Assert
            assertThrows(PagoNoEncontradoException.class,
                    () -> servicio.consultarPago(UUID.randomUUID(), TipoReferencia.BOLETOS, idReferencia));
        }

        @Test
        void consultarPago_cuandoNoExiste_lanzaPagoNoEncontrado() {
            // Arrange
            when(movimientoRepository.findByTipoAndTipoReferenciaAndIdReferencia(
                    TipoMovimiento.PAGO, TipoReferencia.BOLETOS, idReferencia)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(PagoNoEncontradoException.class,
                    () -> servicio.consultarPago(null, TipoReferencia.BOLETOS, idReferencia));
        }
    }
}
