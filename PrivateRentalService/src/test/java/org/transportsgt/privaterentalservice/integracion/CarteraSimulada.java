package org.transportsgt.privaterentalservice.integracion;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servidor HTTP mínimo que hace de Cartera en las pruebas de integración. Cada alquiler (Clave-Idempotencia)
 * puede tener una respuesta programada; sin ella, POST /cartera/pagos responde 503 (Cartera caída) y
 * GET /cartera/pagos responde 404 (no hay pago), como espera la saga.
 */
public class CarteraSimulada {

    private final HttpServer servidor;
    private final Map<String, Respuesta> respuestas = new ConcurrentHashMap<>();

    public record Respuesta(int status, String cuerpo) {
    }

    public CarteraSimulada() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        servidor.createContext("/v1/customers/cartera/pagos", this::atender);
        servidor.start();
    }

    public String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1/customers";
    }

    public void responder(UUID idAlquiler, int status, String cuerpo) {
        respuestas.put(idAlquiler.toString(), new Respuesta(status, cuerpo));
    }

    private void atender(HttpExchange intercambio) throws IOException {
        Respuesta respuesta;
        if ("POST".equals(intercambio.getRequestMethod())) {
            String clave = intercambio.getRequestHeaders().getFirst("Clave-Idempotencia");
            respuesta = clave == null ? null : respuestas.get(clave);
            if (respuesta == null) {
                respuesta = new Respuesta(503, "{\"codigo\":\"SERVICIO_NO_DISPONIBLE\"}");
            }
        } else {
            respuesta = new Respuesta(404, "{\"codigo\":\"PAGO_NO_ENCONTRADO\"}");
        }
        byte[] cuerpo = respuesta.cuerpo().getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(respuesta.status(), cuerpo.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(cuerpo);
        }
    }
}
