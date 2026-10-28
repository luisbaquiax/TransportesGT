package org.transportsgt.ticketservice.clients;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.transportsgt.ticketservice.clients.dto.PagoCarteraConsulta;
import org.transportsgt.ticketservice.clients.dto.PagoCarteraRequest;
import org.transportsgt.ticketservice.clients.dto.PagoCarteraResponse;
import org.transportsgt.ticketservice.exception.CarteraNoDisponibleException;
import org.transportsgt.ticketservice.exception.PagoRechazadoException;
import org.transportsgt.eventos.payload.costos.TipoReferencia;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Con los registros reales de Resilience4j (configurados como en application.properties, sin esperas).
 */
@ExtendWith(MockitoExtension.class)
class CarteraPasarelaPagoTest {

    private static final String CLAVE = "clave-interna";
    private static final LocalDate FECHA = LocalDate.parse("2026-10-20");

    @Mock
    private CarteraClient cliente;

    private CarteraPasarelaPago pasarela;
    private UUID idCompra;

    @BeforeEach
    void setUp() {
        RetryRegistry retry = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(1))
                .retryExceptions(ResourceAccessException.class, HttpServerErrorException.class)
                .build());
        CircuitBreakerRegistry circuito = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .recordExceptions(ResourceAccessException.class, HttpServerErrorException.class)
                .build());
        pasarela = new CarteraPasarelaPago(cliente, retry, circuito, CLAVE, JsonMapper.builder().build());
        idCompra = UUID.randomUUID();
    }

    private static HttpClientErrorException errorCliente(HttpStatus status, String cuerpo) {
        return HttpClientErrorException.create(status, status.getReasonPhrase(), HttpHeaders.EMPTY,
                cuerpo.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    private UUID cobrar() {
        return pasarela.cobrar(
                "jwt", idCompra,
                new BigDecimal("170.00"),
                FECHA,
                "Compra de 2 boleto(s)");
    }

    @Nested
    class Cobrar {

        @Test
        void cobrar_reenviaElJwtYUsaIdCompraComoClaveDeIdempotencia() {
            // Arrange
            UUID idPago = UUID.randomUUID();
            ArgumentCaptor<PagoCarteraRequest> captor = ArgumentCaptor.forClass(PagoCarteraRequest.class);
            when(cliente.pagar(eq("Bearer jwt"), eq(idCompra.toString()), captor.capture()))
                    .thenReturn(new PagoCarteraResponse(idPago, new BigDecimal("30.00")));

            // Act
            UUID resultado = cobrar();

            // Assert
            assertAll(
                    () -> assertEquals(idPago, resultado),
                    () -> assertEquals(TipoReferencia.BOLETOS, captor.getValue().tipoReferencia()),
                    () -> assertEquals(idCompra, captor.getValue().idReferencia()),
                    () -> assertEquals(new BigDecimal("170.00"), captor.getValue().monto())
            );
        }

        @Test
        void cobrar_cuandoCarteraRechaza_propagaStatusYCodigo() {
            // Arrange
            when(cliente.pagar(anyString(), anyString(), any()))
                    .thenThrow(errorCliente(HttpStatus.UNPROCESSABLE_CONTENT,
                    "{\"codigo\":\"SALDO_INSUFICIENTE\",\"error\":\"Saldo insuficiente\"}"));

            // Act
            PagoRechazadoException ex = assertThrows(PagoRechazadoException.class, CarteraPasarelaPagoTest.this::cobrar);

            // Assert
            assertAll(
                    () -> assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, ex.getStatus()),
                    () -> assertEquals("SALDO_INSUFICIENTE", ex.getCodigo()),
                    () -> assertEquals("Saldo insuficiente", ex.getMessage())
            );
            verify(cliente, times(1)).pagar(anyString(), anyString(), any());
        }

        @Test
        void cobrar_cuandoElRechazoNoTraeCuerpoLegible_usaCodigoGenerico() {
            // Arrange
            when(cliente.pagar(anyString(), anyString(), any()))
                    .thenThrow(errorCliente(HttpStatus.FORBIDDEN, "<html>"));

            // Act
            PagoRechazadoException ex = assertThrows(PagoRechazadoException.class, CarteraPasarelaPagoTest.this::cobrar);

            // Assert
            assertAll(
                    () -> assertEquals(HttpStatus.FORBIDDEN, ex.getStatus()),
                    () -> assertEquals("PAGO_RECHAZADO", ex.getCodigo())
            );
        }

        @Test
        void cobrar_cuandoCarteraFallaConError5xx_reintentaTresVecesYReportaNoDisponible() {
            // Arrange
            when(cliente.pagar(anyString(), anyString(), any()))
                    .thenThrow(HttpServerErrorException
                            .create(HttpStatus.BAD_GATEWAY, "Bad Gateway", HttpHeaders.EMPTY,
                            new byte[0], StandardCharsets.UTF_8));

            // Act & Assert
            assertThrows(CarteraNoDisponibleException.class, CarteraPasarelaPagoTest.this::cobrar);
            verify(cliente, times(3)).pagar(anyString(), anyString(), any());
        }

        @Test
        void cobrar_cuandoUnReintentoResponde_devuelveElPago() {
            // Arrange
            UUID idPago = UUID.randomUUID();
            when(cliente.pagar(anyString(), anyString(), any()))
                    .thenThrow(new ResourceAccessException("timeout"))
                    .thenReturn(new PagoCarteraResponse(idPago, BigDecimal.ZERO));

            // Act & Assert
            assertEquals(idPago, cobrar());
        }

        @Test
        void cobrar_conElCircuitoAbierto_noLlamaACartera() {
            // Arrange: a la 5.ª falla (mínimo de llamadas) el circuito se abre y corta el último reintento
            when(cliente.pagar(anyString(), anyString(), any())).thenThrow(new ResourceAccessException("timeout"));
            assertThrows(CarteraNoDisponibleException.class, CarteraPasarelaPagoTest.this::cobrar);
            assertThrows(CarteraNoDisponibleException.class, CarteraPasarelaPagoTest.this::cobrar);

            // Act
            CarteraNoDisponibleException ex = assertThrows(CarteraNoDisponibleException.class,
                    CarteraPasarelaPagoTest.this::cobrar);

            // Assert
            assertTrue(ex.getMessage().contains("circuito abierto"));
            verify(cliente, times(5)).pagar(anyString(), anyString(), any());
        }
    }

    @Nested
    class ConsultarPago {

        @Test
        void consultarPago_usaLaClaveInternaYDevuelveElPago() {
            // Arrange
            UUID idPago = UUID.randomUUID();
            when(cliente.consultar(CLAVE, TipoReferencia.BOLETOS, idCompra))
                    .thenReturn(new PagoCarteraConsulta(idPago, new BigDecimal("170.00"), FECHA));

            // Act & Assert
            assertEquals(Optional.of(idPago), pasarela.consultarPago(idCompra));
        }

        @Test
        void consultarPago_cuandoNoHayPago_devuelveVacio() {
            // Arrange
            when(cliente.consultar(CLAVE, TipoReferencia.BOLETOS, idCompra))
                    .thenThrow(errorCliente(HttpStatus.NOT_FOUND, ""));

            // Act & Assert
            assertEquals(Optional.empty(), pasarela.consultarPago(idCompra));
        }

        @Test
        void consultarPago_cuandoLaClaveEsRechazada_reportaNoDisponible() {
            // Arrange
            when(cliente.consultar(CLAVE, TipoReferencia.BOLETOS, idCompra))
                    .thenThrow(errorCliente(HttpStatus.UNAUTHORIZED, ""));

            // Act & Assert
            assertThrows(CarteraNoDisponibleException.class, () -> pasarela.consultarPago(idCompra));
        }

        @Test
        void consultarPago_cuandoCarteraNoResponde_reportaNoDisponible() {
            // Arrange
            when(cliente.consultar(CLAVE, TipoReferencia.BOLETOS, idCompra))
                    .thenThrow(new ResourceAccessException("x"));

            // Act & Assert
            assertThrows(CarteraNoDisponibleException.class, () -> pasarela.consultarPago(idCompra));
            verify(cliente, times(3)).consultar(CLAVE, TipoReferencia.BOLETOS, idCompra);
        }
    }
}
