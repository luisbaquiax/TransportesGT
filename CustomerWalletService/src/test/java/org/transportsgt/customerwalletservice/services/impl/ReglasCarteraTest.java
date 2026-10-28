package org.transportsgt.customerwalletservice.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.transportsgt.customerwalletservice.exception.ClaveIdempotenciaRequeridaException;
import org.transportsgt.customerwalletservice.exception.ClaveIdempotenciaReutilizadaException;
import org.transportsgt.customerwalletservice.exception.CuentaInactivaException;
import org.transportsgt.customerwalletservice.exception.SaldoInsuficienteException;
import org.transportsgt.customerwalletservice.fixture.CarteraFixture;
import org.transportsgt.customerwalletservice.fixture.ClienteFixture;
import org.transportsgt.customerwalletservice.models.Cartera;
import org.transportsgt.customerwalletservice.models.MovimientoCartera;
import org.transportsgt.customerwalletservice.models.TipoMovimiento;
import org.transportsgt.customerwalletservice.utils.Dinero;
import org.transportsgt.eventos.payload.costos.TipoReferencia;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReglasCarteraTest {

    private ReglasCartera reglas;
    private Cartera cartera;

    @BeforeEach
    void setUp() {
        reglas = new ReglasCartera(new Dinero());
        cartera = CarteraFixture.cartera(ClienteFixture.cliente(UUID.randomUUID()), "150.00");
    }

    @Nested
    class Acreditar {

        @Test
        void acreditar_sumaElMontoYDevuelveElSaldoConDosDecimales() {
            // Act
            BigDecimal saldoPosterior = reglas.acreditar(cartera, new BigDecimal("20.005"));

            // Assert
            assertAll(
                    () -> assertEquals(new BigDecimal("170.01"), saldoPosterior),
                    () -> assertEquals(new BigDecimal("170.01"), cartera.getSaldo())
            );
        }

        @Test
        void acreditar_cuandoElMontoNoEsPositivo_lanzaIllegalArgumentSinCambiarElSaldo() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> reglas.acreditar(cartera, BigDecimal.ZERO)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> reglas.acreditar(cartera, new BigDecimal("-1"))),
                    () -> assertThrows(IllegalArgumentException.class, () -> reglas.acreditar(cartera, null)),
                    () -> assertEquals(new BigDecimal("150.00"), cartera.getSaldo())
            );
        }
    }

    @Nested
    class Debitar {

        @Test
        void debitar_cuandoAlcanzaElSaldo_restaYDevuelveElSaldoPosterior() {
            // Act
            BigDecimal saldoPosterior = reglas.debitar(cartera, new BigDecimal("150.00"));

            // Assert
            assertAll(
                    () -> assertEquals(new BigDecimal("0.00"), saldoPosterior),
                    () -> assertEquals(new BigDecimal("0.00"), cartera.getSaldo())
            );
        }

        @Test
        void debitar_cuandoNoAlcanzaElSaldo_lanzaSaldoInsuficienteSinCambiarElSaldo() {
            // Act & Assert
            assertThrows(SaldoInsuficienteException.class, () -> reglas.debitar(cartera, new BigDecimal("150.01")));
            assertEquals(new BigDecimal("150.00"), cartera.getSaldo());
        }

        @Test
        void debitar_cuandoElMontoNoEsPositivo_lanzaIllegalArgument() {
            assertThrows(IllegalArgumentException.class, () -> reglas.debitar(cartera, BigDecimal.ZERO));
        }
    }

    @Nested
    class ValidarClave {

        @Test
        void validarClave_quitaEspaciosAlrededor() {
            assertEquals("abc-123", reglas.validarClave("  abc-123 "));
        }

        @Test
        void validarClave_cuandoFaltaOEsDemasiadoLarga_lanzaClaveRequerida() {
            assertAll(
                    () -> assertThrows(ClaveIdempotenciaRequeridaException.class, () -> reglas.validarClave(null)),
                    () -> assertThrows(ClaveIdempotenciaRequeridaException.class, () -> reglas.validarClave(" ")),
                    () -> assertThrows(ClaveIdempotenciaRequeridaException.class,
                            () -> reglas.validarClave("x".repeat(ReglasCartera.LARGO_MAXIMO_CLAVE + 1)))
            );
        }
    }

    @Nested
    class ValidarActiva {

        @Test
        void validarActiva_cuandoElClienteEstaInactivo_lanzaCuentaInactiva() {
            assertAll(
                    () -> assertDoesNotThrow(() -> reglas.validarActiva(ClienteFixture.cliente(UUID.randomUUID()))),
                    () -> assertThrows(CuentaInactivaException.class,
                            () -> reglas.validarActiva(ClienteFixture.inactivo(UUID.randomUUID())))
            );
        }
    }

    @Nested
    class VerificarRepetido {

        @Test
        void verificarRepetido_cuandoCoincidenTipoCarteraYMonto_devuelveElOriginal() {
            // Arrange
            MovimientoCartera original = CarteraFixture.recarga(cartera, "200.00", "clave");

            // Act & Assert
            assertSame(original, reglas.verificarRepetido(original, TipoMovimiento.RECARGA, cartera,
                    new BigDecimal("200")));
        }

        @Test
        void verificarRepetido_cuandoCambiaTipoCarteraOMonto_lanzaClaveReutilizada() {
            // Arrange
            MovimientoCartera recarga = CarteraFixture.recarga(cartera, "200.00", "clave");
            MovimientoCartera pago = CarteraFixture.pago(cartera, TipoReferencia.BOLETOS, UUID.randomUUID(), "200.00");
            Cartera otra = CarteraFixture.cartera(ClienteFixture.cliente(UUID.randomUUID()), "0.00");

            // Act & Assert
            assertAll(
                    () -> assertThrows(ClaveIdempotenciaReutilizadaException.class, () -> reglas.verificarRepetido(
                            pago, TipoMovimiento.RECARGA, cartera, new BigDecimal("200.00"))),
                    () -> assertThrows(ClaveIdempotenciaReutilizadaException.class, () -> reglas.verificarRepetido(
                            recarga, TipoMovimiento.RECARGA, otra, new BigDecimal("200.00"))),
                    () -> assertThrows(ClaveIdempotenciaReutilizadaException.class, () -> reglas.verificarRepetido(
                            recarga, TipoMovimiento.RECARGA, cartera, new BigDecimal("200.01")))
            );
        }
    }
}
