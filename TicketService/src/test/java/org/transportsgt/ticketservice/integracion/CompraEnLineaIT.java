package org.transportsgt.ticketservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.EstadoViaje;
import org.transportsgt.ticketservice.jobs.RecuperacionComprasJob;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Saga de la compra en línea contra PostgreSQL y RabbitMQ reales, con Cartera simulada.
 */
class CompraEnLineaIT extends IntegracionBase {

    @Autowired
    private RecuperacionComprasJob job;

    @Test
    void comprar_cuandoCarteraCobra_confirmaYDejaLaVentaEnElOutbox() throws Exception {
        // Arrange
        UUID idViaje = viajeALaVenta();
        String nit = nitNuevo();
        UUID comprador = cliente(nit);
        UUID idCompra = UUID.randomUUID();
        carteraAprueba();

        // Act
        comprar(comprador, idCompra, idViaje, 13, 12)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/boletos/compras/" + idCompra))
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.montoTotal").value(170.00))
                .andExpect(jsonPath("$.nitComprador").value(nit))
                .andExpect(jsonPath("$.asientos.length()").value(2));

        // Assert
        CARTERA.verify(postRequestedFor(urlPathEqualTo(PAGOS))
                .withHeader("Clave-Idempotencia", equalTo(idCompra.toString()))
                .withHeader("Authorization", com.github.tomakehurst.wiremock.client.WireMock.matching("Bearer .+"))
                .withRequestBody(equalToJson("{\"tipoReferencia\":\"BOLETOS\",\"monto\":\"170.00\"}", true, true)));
        Map<String, Object> fila = jdbc.queryForMap("""
                SELECT llave_ruteo, version_agregado, datos -> 'asientos' AS asientos, datos ->> 'idPago' AS id_pago
                  FROM outbox_evento WHERE tipo_evento = 'VentaBoletosRegistrada' AND id_agregado = ?
                """, idCompra);
        assertAll(
                () -> assertEquals(TipoEvento.VENTA_BOLETOS_REGISTRADA.llaveRuteo(), fila.get("llave_ruteo")),
                () -> assertEquals("[12, 13]", fila.get("asientos").toString()),
                () -> assertNotNull(fila.get("id_pago"))
        );
        esperar("outbox publicado", () -> jdbc.queryForObject("""
                SELECT count(*) FROM outbox_evento WHERE id_agregado = ? AND publicado_en IS NOT NULL
                """, Integer.class, idCompra) == 1);
    }

    @Test
    void comprar_cuandoSeRepiteElIdCompra_registraUnaCompraYUnSoloCobro() throws Exception {
        // Arrange
        UUID idViaje = viajeALaVenta();
        UUID comprador = cliente(nitNuevo());
        UUID idCompra = UUID.randomUUID();
        carteraAprueba();
        comprar(comprador, idCompra, idViaje, 5).andExpect(status().isCreated());

        // Act & Assert
        comprar(comprador, idCompra, idViaje, 5).andExpect(status().isOk());
        comprar(comprador, idCompra, idViaje, 6)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("COMPRA_DUPLICADA"));
        assertAll(
                () -> assertEquals(1, compras(idCompra)),
                () -> assertEquals(1, cobrosRecibidos()),
                () -> assertEquals(1, outbox(TipoEvento.VENTA_BOLETOS_REGISTRADA, idCompra))
        );
    }

    @Test
    void comprar_cuandoDosClientesPidenElMismoAsientoALaVez_soloUnoLoObtiene() throws Exception {
        // Arrange
        UUID idViaje = viajeALaVenta();
        List<UUID> compradores = List.of(cliente(nitNuevo()), cliente(nitNuevo()), cliente(nitNuevo()));
        carteraAprueba();
        CountDownLatch salida = new CountDownLatch(1);
        List<Callable<Integer>> intentos = new ArrayList<>();
        for (UUID comprador : compradores) {
            intentos.add(() -> {
                salida.await();
                return comprar(comprador, UUID.randomUUID(), idViaje, 20).andReturn().getResponse().getStatus();
            });
        }

        // Act
        List<Integer> estados = new ArrayList<>();
        try (ExecutorService hilos = Executors.newFixedThreadPool(compradores.size())) {
            List<Future<Integer>> resultados = intentos.stream().map(hilos::submit).toList();
            salida.countDown();
            for (Future<Integer> resultado : resultados) {
                estados.add(resultado.get());
            }
        }

        // Assert
        assertAll(
                () -> assertEquals(List.of(201, 409, 409), estados.stream().sorted().toList()),
                () -> assertEquals(1, jdbc.queryForObject("""
                        SELECT count(*) FROM boleto WHERE id_viaje = ? AND numero_asiento = 20 AND estado <> 'ANULADO'
                        """, Integer.class, idViaje)),
                () -> assertEquals(1, cobrosRecibidos())
        );
    }

    @Test
    void comprar_cuandoNoAlcanzaElSaldo_responde422YLiberaLosAsientos() throws Exception {
        // Arrange
        UUID idViaje = viajeALaVenta();
        UUID comprador = cliente(nitNuevo());
        UUID idCompra = UUID.randomUUID();
        carteraResponde(post(urlPathEqualTo(PAGOS)), 422,
                "{\"status\":422,\"error\":\"Saldo insuficiente: disponible Q10.00\",\"codigo\":\"SALDO_INSUFICIENTE\"}");

        // Act
        comprar(comprador, idCompra, idViaje, 8)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SALDO_INSUFICIENTE"))
                .andExpect(jsonPath("$.error").value("Saldo insuficiente: disponible Q10.00"));

        // Assert
        assertAll(
                () -> assertEquals("ANULADA", estadoCompra(idCompra)),
                () -> assertEquals(0, outbox(TipoEvento.VENTA_BOLETOS_REGISTRADA, idCompra)),
                () -> assertEquals(0, outbox(TipoEvento.BOLETOS_ANULADOS, idCompra)),
                () -> assertEquals(1, cobrosRecibidos())
        );
        mockMvc.perform(conToken(get("/boletos/viajes/" + idViaje + "/asientos"), token(comprador)))
                .andExpect(jsonPath("$.ocupados.length()").value(0));
    }

    @Test
    void comprar_cuandoCarteraNoResponde_quedaReservadaYElJobLaConfirma() throws Exception {
        // Arrange
        UUID idViaje = viajeALaVenta();
        UUID comprador = cliente(nitNuevo());
        UUID idCompra = UUID.randomUUID();
        carteraResponde(post(urlPathEqualTo(PAGOS)), 503, "{}");

        // Act
        comprar(comprador, idCompra, idViaje, 3)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estado").value("RESERVADA"));
        // Cartera sí cobró (la respuesta se perdió) y la compra ya tiene la antigüedad para el job
        UUID idPago = UUID.randomUUID();
        carteraTienePago(idCompra, idPago);
        jdbc.update("UPDATE compra SET reserva_expira_en = now() + interval '1 minute' WHERE id = ?", idCompra);
        job.ejecutar();

        // Assert
        assertAll(
                () -> assertEquals(3, cobrosRecibidos()),
                () -> assertEquals("CONFIRMADA", estadoCompra(idCompra)),
                () -> assertEquals(idPago, jdbc.queryForObject("SELECT id_pago FROM compra WHERE id = ?", UUID.class,
                        idCompra)),
                () -> assertEquals(1, outbox(TipoEvento.VENTA_BOLETOS_REGISTRADA, idCompra))
        );
    }

    @Test
    void comprar_cuandoCarteraNoRespondeYNoHuboPago_elJobLiberaLaReservaVencida() throws Exception {
        // Arrange
        UUID idViaje = viajeALaVenta();
        UUID comprador = cliente(nitNuevo());
        UUID idCompra = UUID.randomUUID();
        carteraResponde(post(urlPathEqualTo(PAGOS)), 503, "{}");
        comprar(comprador, idCompra, idViaje, 4).andExpect(status().isAccepted());
        jdbc.update("UPDATE compra SET reserva_expira_en = now() - interval '1 second' WHERE id = ?", idCompra);

        // Act: sin stub de consulta, Cartera responde 404 (no hubo pago)
        job.ejecutar();

        // Assert
        assertAll(
                () -> assertEquals("ANULADA", estadoCompra(idCompra)),
                () -> assertEquals("RESERVA_VENCIDA", jdbc.queryForObject(
                        "SELECT motivo_anulacion FROM compra WHERE id = ?", String.class, idCompra)),
                () -> assertEquals(0, jdbc.queryForObject("""
                        SELECT count(*) FROM boleto WHERE id_compra = ? AND estado <> 'ANULADO'
                        """, Integer.class, idCompra))
        );
    }

    @Test
    void comprar_cuandoElViajeSeEliminaDuranteElCobro_publicaOtroBoletosAnuladosConElPago() throws Exception {
        // Arrange: Cartera tarda en cobrar
        UUID idViaje = viajeALaVenta();
        UUID comprador = cliente(nitNuevo());
        UUID idCompra = UUID.randomUUID();
        CARTERA.stubFor(post(urlPathEqualTo(PAGOS)).willReturn(com.github.tomakehurst.wiremock.client.WireMock
                .aResponse().withStatus(201).withHeader("Content-Type", "application/json").withFixedDelay(2000)
                .withBody("{\"idPago\":\"" + UUID.randomUUID() + "\",\"saldoPosterior\":\"0.00\"}")));

        // Act
        MvcResult resultado;
        try (ExecutorService hilo = Executors.newSingleThreadExecutor()) {
            Future<MvcResult> compra = hilo.submit(() -> comprar(comprador, idCompra, idViaje, 9).andReturn());
            esperar("compra reservada", () -> compras(idCompra) == 1);
            publicarYEsperar(TipoEvento.VIAJE_ELIMINADO, sobre(TipoEvento.VIAJE_ELIMINADO, idViaje, 1L,
                    new org.transportsgt.eventos.payload.DatosViajeEliminado(idViaje,
                            org.transportsgt.eventos.payload.TipoViaje.REGULAR, null, java.time.Instant.now(),
                            UUID.randomUUID())));
            resultado = compra.get();
        }

        // Assert
        List<Map<String, Object>> anulaciones = jdbc.queryForList("""
                SELECT datos ->> 'idPago' AS id_pago FROM outbox_evento
                 WHERE tipo_evento = 'BoletosAnulados' AND id_agregado = ? ORDER BY version_agregado
                """, idCompra);
        assertAll(
                () -> assertEquals(409, resultado.getResponse().getStatus()),
                () -> assertEquals("VIAJE_NO_DISPONIBLE", leer(resultado).get("codigo").asString()),
                () -> assertEquals("ANULADA", estadoCompra(idCompra)),
                () -> assertEquals(2, anulaciones.size()),
                () -> assertEquals(null, anulaciones.get(0).get("id_pago")),
                () -> assertNotNull(anulaciones.get(1).get("id_pago"))
        );
    }

    @Test
    void comprar_cuandoElViajeNoEstaProgramadoOYaSalio_responde409() throws Exception {
        // Arrange
        UUID comprador = cliente(nitNuevo());
        UUID enTransito = UUID.randomUUID();
        UUID salido = UUID.randomUUID();
        publicarYEsperar(TipoEvento.VIAJE_PROGRAMADO, sobre(TipoEvento.VIAJE_PROGRAMADO, enTransito, 3L,
                datosViaje(enTransito, EstadoViaje.EN_TRANSITO, manana())));
        publicarYEsperar(TipoEvento.VIAJE_PROGRAMADO, sobre(TipoEvento.VIAJE_PROGRAMADO, salido, 0L,
                datosViaje(salido, EstadoViaje.PROGRAMADO, manana().minusDays(2))));

        // Act & Assert
        comprar(comprador, UUID.randomUUID(), enTransito, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VIAJE_NO_DISPONIBLE"));
        comprar(comprador, UUID.randomUUID(), salido, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("El viaje ya salió"));
        assertEquals(0, cobrosRecibidos());
    }
}
