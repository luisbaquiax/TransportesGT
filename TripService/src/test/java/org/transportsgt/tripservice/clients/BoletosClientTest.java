package org.transportsgt.tripservice.clients;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.transportsgt.tripservice.config.BoletosProperties;
import org.transportsgt.tripservice.exception.ServicioNoDisponibleException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contra un servidor HTTP real (JDK): RestClient con request factory propia no aplica MockRestServiceServer.
 */
class BoletosClientTest {

    private static final String CLAVE_INTERNA = "clave-interna-prueba";

    private HttpServer servidor;
    private String baseUrl;
    private final AtomicInteger peticiones = new AtomicInteger();
    private volatile int codigo = 200;
    private volatile String cuerpo = "{\"asientoMaximo\":42}";
    private volatile String claveRecibida;

    @BeforeEach
    void arrancar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/v1/tickets/boletos/viajes", exchange -> {
            peticiones.incrementAndGet();
            claveRecibida = exchange.getRequestHeaders().getFirst(BoletosClient.CABECERA_CLAVE_INTERNA);
            byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(codigo, bytes.length);
            try (OutputStream salida = exchange.getResponseBody()) {
                salida.write(bytes);
            }
        });
        servidor.start();
        baseUrl = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1/tickets";
    }

    @AfterEach
    void parar() {
        servidor.stop(0);
    }

    private BoletosClient cliente() {
        return new BoletosClient(org.springframework.web.client.RestClient.builder(),
                new BoletosProperties(baseUrl), CLAVE_INTERNA);
    }

    @Test
    void devuelveAsientoMaximo() {
        UUID idViaje = UUID.randomUUID();
        cuerpo = "{\"idViaje\":\"" + idViaje + "\",\"asientoMaximo\":42}";
        assertEquals(42, cliente().asientoMaximo(idViaje));
        assertEquals(1, peticiones.get());
    }

    @Test
    void enviaLaClaveInterna() {
        cliente().asientoMaximo(UUID.randomUUID());
        assertEquals(CLAVE_INTERNA, claveRecibida);
    }

    @Test
    void cuerpoNuloDevuelveCero() {
        cuerpo = "null";
        assertEquals(0, cliente().asientoMaximo(UUID.randomUUID()));
    }

    @Test
    void errorDelServidorSeConvierteEn503() {
        codigo = 500;
        assertThrows(ServicioNoDisponibleException.class,
                () -> cliente().asientoMaximo(UUID.randomUUID()));
        assertTrue(peticiones.get() >= 1);
    }
}
