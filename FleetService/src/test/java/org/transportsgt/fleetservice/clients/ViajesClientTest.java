package org.transportsgt.fleetservice.clients;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.transportsgt.fleetservice.config.ViajesProperties;
import org.transportsgt.fleetservice.exception.ViajesNoDisponibleException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contra un servidor HTTP real (JDK): ViajesClient cambia el request factory para fijar
 * timeouts, así que un MockRestServiceServer no le aplica.
 */
class ViajesClientTest {

    private HttpServer servidor;
    private String baseUrl;
    private final AtomicInteger peticiones = new AtomicInteger();
    private volatile int codigo = 200;
    private volatile String cuerpo = "[]";

    @BeforeEach
    void arrancar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/v1/trips/viajes/activos", exchange -> {
            peticiones.incrementAndGet();
            byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(codigo, bytes.length);
            try (OutputStream salida = exchange.getResponseBody()) {
                salida.write(bytes);
            }
        });
        servidor.start();
        baseUrl = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1/trips";
    }

    @AfterEach
    void parar() {
        servidor.stop(0);
    }

    private ViajesClient cliente() {
        return new ViajesClient(org.springframework.web.client.RestClient.builder(),
                new ViajesProperties(baseUrl));
    }

    @Test
    void devuelveTrueCuandoHayViajes() {
        UUID idBus = UUID.randomUUID();
        cuerpo = "[{\"idViaje\":\"" + idBus + "\"}]";

        assertTrue(cliente().hayViajesActivos(idBus));
        assertEquals(1, peticiones.get());
    }

    @Test
    void devuelveFalseCuandoLaListaEstaVacia() {
        cuerpo = "[]";
        assertFalse(cliente().hayViajesActivos(UUID.randomUUID()));
    }

    @Test
    void errorDelServidorSeConvierteEn503TrasReintentar() {
        codigo = 500;
        UUID idBus = UUID.randomUUID();

        assertThrows(ViajesNoDisponibleException.class, () -> cliente().hayViajesActivos(idBus));
        assertEquals(3, peticiones.get(), "1 intento + 2 reintentos");
    }
}
