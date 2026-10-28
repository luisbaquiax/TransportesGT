package org.transportsgt.fleetservice.integracion;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Con un Viajes simulado (servidor HTTP del JDK): desactivar un bus sin viajes activos, con viajes activos,
 * y eventos de salida o llegada de un bus que Flota no conoce.
 */
class ViajesSimuladoIT extends IntegracionBase {

    private static final Set<String> BUSES_CON_VIAJES = ConcurrentHashMap.newKeySet();
    private static final HttpServer VIAJES = arrancar();

    private static HttpServer arrancar() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/v1/trips/viajes/activos", exchange -> {
                String consulta = exchange.getRequestURI().getQuery();
                String idBus = consulta == null ? "" : consulta.replace("idBus=", "");
                byte[] cuerpo = (BUSES_CON_VIAJES.contains(idBus) ? "[{\"id\":\"x\"}]" : "[]")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, cuerpo.length);
                try (OutputStream salida = exchange.getResponseBody()) {
                    salida.write(cuerpo);
                }
            });
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void viajesSimulado(DynamicPropertyRegistry registro) {
        registro.add("viajes.url", () -> "http://127.0.0.1:" + VIAJES.getAddress().getPort() + "/v1/trips");
        registro.add("viajes.clave-interna", () -> "clave-interna-prueba");
    }

    @AfterAll
    static void parar() {
        VIAJES.stop(0);
    }

    private org.springframework.test.web.servlet.ResultActions desactivar(String id) throws Exception {
        return mockMvc.perform(conToken(patch("/buses/" + id + "/activo"), tokenAdminSucursal())
                .header("X-Correlation-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"));
    }

    @Test
    void busSinViajesActivosSeDesactiva() throws Exception {
        String id = crearBus("V" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        desactivar(id).andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    void busConViajesActivosNoSeDesactiva() throws Exception {
        String id = crearBus("W" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        BUSES_CON_VIAJES.add(id);
        desactivar(id).andExpect(status().isConflict());
    }

    @Test
    void cambioDeEstadoOperativoIdaYVuelta() throws Exception {
        String id = crearBus("T" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        for (String estado : new String[]{"EN_TALLER", "DISPONIBLE"}) {
            mockMvc.perform(conToken(patch("/buses/" + id + "/estado-operativo"), tokenAdminSucursal())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"estadoOperativo\":\"" + estado + "\"}"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void salidaYLlegadaDeUnBusDesconocido() {
        UUID idBus = UUID.randomUUID();
        UUID salida = UUID.randomUUID();
        UUID llegada = UUID.randomUUID();
        rabbitTemplate.convertAndSend("transporte.eventos", rutaDe("salida"), """
                {"idEvento":"%s","tipoEvento":"SalidaRegistrada","versionEvento":1,
                 "ocurridoEn":"2026-10-17T12:00:00Z","servicioProductor":"servicio-viajes",
                 "idAgregado":"%s","versionAgregado":1,"idCorrelacion":"%s",
                 "datos":{"idViaje":"%s","idBus":"%s","idChofer":"%s",
                          "fechaHoraSalidaReal":"2026-10-17T12:00:00-06:00",
                          "kilometrajeInicial":400,"idUsuarioRegistro":"%s"}}
                """.formatted(salida, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), idBus,
                UUID.randomUUID(), UUID.randomUUID()));
        rabbitTemplate.convertAndSend("transporte.eventos", rutaDe("llegada"), """
                {"idEvento":"%s","tipoEvento":"LlegadaRegistrada","versionEvento":1,
                 "ocurridoEn":"2026-10-17T16:00:00Z","servicioProductor":"servicio-viajes",
                 "idAgregado":"%s","versionAgregado":2,"idCorrelacion":"%s",
                 "datos":{"idViaje":"%s","tipoViaje":"REGULAR","idSucursalOrigen":"%s","idBus":"%s",
                          "idSucursalBus":"%s","idChofer":"%s","salarioBaseChofer":"150.00",
                          "fechaHoraSalidaReal":"2026-10-17T12:00:00-06:00",
                          "fechaHoraLlegadaReal":"2026-10-17T16:00:00-06:00",
                          "kilometrajeInicial":400,"kilometrajeFinal":600,"distanciaRecorridaKm":200,
                          "gastoCombustibleTotal":"300.00","idUsuarioRegistro":"%s"}}
                """.formatted(llegada, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), XELA, idBus, XELA,
                UUID.randomUUID(), UUID.randomUUID()));

        for (UUID idEvento : new UUID[]{salida, llegada}) {
            esperar(() -> jdbc.queryForObject("SELECT count(*) FROM evento_procesado WHERE id_evento = ?",
                    Integer.class, idEvento) == 1, 15);
        }
    }
}
