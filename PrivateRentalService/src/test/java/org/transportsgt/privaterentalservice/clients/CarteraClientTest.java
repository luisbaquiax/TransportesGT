package org.transportsgt.privaterentalservice.clients;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.transportsgt.eventos.contexto.ContextoCorrelacion;
import org.transportsgt.privaterentalservice.exception.CarteraRechazoException;
import org.transportsgt.privaterentalservice.exception.ServicioNoDisponibleException;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contra un servidor HTTP real (JDK): RestClient con request factory propia no aplica MockRestServiceServer.
 */
class CarteraClientTest {

    private static final UUID ID_PAGO = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final String JWT = "Bearer token-cliente";

    private HttpServer servidor;
    private String baseUrl;
    private final AtomicInteger peticiones = new AtomicInteger();
    private volatile int codigo = 200;
    private volatile String cuerpo = "{}";
    private volatile Headers ultimasCabeceras;
    private volatile String ultimoCuerpo;

    @BeforeEach
    void arrancar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/v1/customers/cartera/pagos", exchange -> {
            peticiones.incrementAndGet();
            ultimasCabeceras = exchange.getRequestHeaders();
            ultimoCuerpo = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(codigo, bytes.length == 0 ? -1 : bytes.length);
            try (OutputStream salida = exchange.getResponseBody()) {
                salida.write(bytes);
            }
        });
        servidor.start();
        baseUrl = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1/customers";
    }

    @AfterEach
    void parar() {
        servidor.stop(0);
        ContextoCorrelacion.limpiar();
    }

    private CarteraClient cliente() {
        return new CarteraClient(RestClient.builder(), baseUrl, "clave-interna-prueba");
    }

    private UUID pagar(CarteraClient cliente, String descripcion) {
        return cliente.pagar(JWT, UUID.randomUUID(), new BigDecimal("150.00"), LocalDate.parse("2026-10-20"), descripcion);
    }

    @Nested
    class Pagar {

        @Test
        void respuestaConIdPago_reenviaJwtYClaveIdempotencia() {
            // Arrange
            cuerpo = "{\"idPago\":\"" + ID_PAGO + "\"}";
            UUID idAlquiler = UUID.randomUUID();
            UUID correlacion = UUID.randomUUID();
            ContextoCorrelacion.fijar(correlacion);

            // Act
            UUID idPago = cliente().pagar(JWT, idAlquiler, new BigDecimal("150.00"), LocalDate.parse("2026-10-20"),
                    "Viaje a Antigua");

            // Assert
            assertEquals(ID_PAGO, idPago);
            assertEquals(JWT, ultimasCabeceras.getFirst("Authorization"));
            assertEquals(idAlquiler.toString(), ultimasCabeceras.getFirst("Clave-Idempotencia"));
            assertEquals(correlacion.toString(), ultimasCabeceras.getFirst("X-Correlation-Id"));
            assertTrue(ultimoCuerpo.contains("\"monto\":\"150.00\""));
            assertTrue(ultimoCuerpo.contains("Viaje a Antigua"));
        }

        @Test
        void sinDescripcionNiCorrelacion_usaDescripcionPorDefecto() {
            // Arrange
            cuerpo = "{\"idPago\":\"" + ID_PAGO + "\"}";

            // Act
            pagar(cliente(), null);

            // Assert
            assertTrue(ultimoCuerpo.contains("Alquiler privado"));
            assertNull(ultimasCabeceras.getFirst("X-Correlation-Id"));
        }

        @Test
        void respuestaSinIdPago_devuelveNull() {
            // Arrange
            cuerpo = "{}";

            // Act / Assert
            assertNull(pagar(cliente(), "x"));
        }

        @Test
        void rechazo4xx_conservaStatusCodigoYMensajeSinReintentar() {
            // Arrange
            codigo = 422;
            cuerpo = "{\"codigo\":\"SALDO_INSUFICIENTE\",\"error\":\"Saldo insuficiente\"}";
            CarteraClient cliente = cliente();

            // Act
            CarteraRechazoException ex = assertThrows(CarteraRechazoException.class, () -> pagar(cliente, "x"));

            // Assert
            assertEquals(422, ex.getStatus().value());
            assertEquals("SALDO_INSUFICIENTE", ex.getCodigo());
            assertEquals("Saldo insuficiente", ex.getMessage());
            assertEquals(1, peticiones.get());
        }

        @Test
        void rechazo4xxSinCuerpoJson_usaCodigoGenerico() {
            // Arrange
            codigo = 409;
            cuerpo = "no es json";
            CarteraClient cliente = cliente();

            // Act
            CarteraRechazoException ex = assertThrows(CarteraRechazoException.class, () -> pagar(cliente, "x"));

            // Assert
            assertEquals(HttpStatus.CONFLICT, ex.getStatus());
            assertEquals("PAGO_RECHAZADO", ex.getCodigo());
        }

        @Test
        void rechazo4xxConCuerpoSinCampos_usaCodigoGenerico() {
            // Arrange
            codigo = 400;
            cuerpo = "{\"otro\":1}";
            CarteraClient cliente = cliente();

            // Act
            CarteraRechazoException ex = assertThrows(CarteraRechazoException.class, () -> pagar(cliente, "x"));

            // Assert
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
            assertEquals("PAGO_RECHAZADO", ex.getCodigo());
            assertEquals("Cartera rechazó el pago del alquiler", ex.getMessage());
        }

        @Test
        void rechazoConStatusDesconocido_seTrataComo422() {
            // Arrange
            codigo = 499;
            cuerpo = "";
            CarteraClient cliente = cliente();

            // Act
            CarteraRechazoException ex = assertThrows(CarteraRechazoException.class, () -> pagar(cliente, "x"));

            // Assert
            assertEquals(422, ex.getStatus().value());
        }

        @Test
        void error5xx_reintentaYFallaCerrado() {
            // Arrange
            codigo = 500;
            CarteraClient cliente = cliente();

            // Act
            assertThrows(ServicioNoDisponibleException.class, () -> pagar(cliente, "x"));

            // Assert
            assertEquals(2, peticiones.get());
        }

        @Test
        void circuitoAbierto_rechazaSinLlamarACartera() {
            // Arrange: cinco fallas abren el circuito (minimumNumberOfCalls = 5, 50 %)
            codigo = 503;
            CarteraClient cliente = cliente();
            for (int i = 0; i < 5; i++) {
                assertThrows(ServicioNoDisponibleException.class, () -> pagar(cliente, "x"));
            }
            int antes = peticiones.get();

            // Act
            assertThrows(ServicioNoDisponibleException.class, () -> pagar(cliente, "x"));

            // Assert
            assertEquals(antes, peticiones.get());
        }
    }

    @Nested
    class ConsultarPago {

        @Test
        void pagoExistente_devuelveIdYFecha() {
            // Arrange
            cuerpo = "{\"idPago\":\"" + ID_PAGO + "\",\"fechaPago\":\"2026-10-20\"}";
            UUID correlacion = UUID.randomUUID();
            ContextoCorrelacion.fijar(correlacion);

            // Act
            Optional<CarteraClient.PagoEncontrado> pago = cliente().consultarPago(UUID.randomUUID());

            // Assert
            assertEquals(new CarteraClient.PagoEncontrado(ID_PAGO, LocalDate.parse("2026-10-20")), pago.orElseThrow());
            assertEquals("clave-interna-prueba", ultimasCabeceras.getFirst("X-Clave-Interna"));
            assertEquals(correlacion.toString(), ultimasCabeceras.getFirst("X-Correlation-Id"));
        }

        @Test
        void pagoSinFecha_devuelveFechaNula() {
            // Arrange
            cuerpo = "{\"idPago\":\"" + ID_PAGO + "\"}";

            // Act
            Optional<CarteraClient.PagoEncontrado> pago = cliente().consultarPago(UUID.randomUUID());

            // Assert
            assertNull(pago.orElseThrow().fechaPago());
            assertNull(ultimasCabeceras.getFirst("X-Correlation-Id"));
        }

        @Test
        void respuestaSinIdPagoOVacia_devuelveVacio() {
            cuerpo = "{}";
            assertTrue(cliente().consultarPago(UUID.randomUUID()).isEmpty());
            cuerpo = "null";
            assertTrue(cliente().consultarPago(UUID.randomUUID()).isEmpty());
        }

        @Test
        void noEncontrado_devuelveVacio() {
            codigo = 404;
            cuerpo = "{\"codigo\":\"PAGO_NO_ENCONTRADO\"}";
            assertTrue(cliente().consultarPago(UUID.randomUUID()).isEmpty());
        }

        @Test
        void error5xx_fallaCerrado() {
            codigo = 500;
            CarteraClient cliente = cliente();
            assertThrows(ServicioNoDisponibleException.class, () -> cliente.consultarPago(UUID.randomUUID()));
        }
    }
}
