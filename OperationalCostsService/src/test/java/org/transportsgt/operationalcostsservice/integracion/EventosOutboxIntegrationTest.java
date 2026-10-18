package org.transportsgt.operationalcostsservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los manejadores de eventos consumen de RabbitMQ (misma topología real):
 * LlegadaRegistrada genera combustible, depreciación y salario del chofer.
 */
class EventosOutboxIntegrationTest extends IntegracionBase {

    @Test
    void llegadaGeneraCostosDeViaje() throws Exception {
        sembrarSucursalYBus();

        UUID idViaje = UUID.randomUUID();
        OffsetDateTime llegada = OffsetDateTime.now().minusHours(2);
        OffsetDateTime salida = llegada.minusHours(4);

        String sobre = """
                {"idEvento":"%s","tipoEvento":"LlegadaRegistrada","versionEvento":1,
                 "ocurridoEn":"%s","servicioProductor":"servicio-viajes",
                 "idAgregado":"%s","versionAgregado":1,"idCorrelacion":"%s",
                 "datos":{"idViaje":"%s","tipoViaje":"REGULAR","idAlquiler":null,"idRuta":null,
                          "idSucursalOrigen":"%s","idBus":"%s","idSucursalBus":"%s",
                          "idChofer":"%s","salarioBaseChofer":"150.00",
                          "fechaHoraSalidaReal":"%s","fechaHoraLlegadaReal":"%s",
                          "kilometrajeInicial":100000.0,"kilometrajeFinal":100205.5,
                          "distanciaRecorridaKm":205.5,"gastoCombustibleTotal":"420.00",
                          "idUsuarioRegistro":"%s"}}
                """.formatted(UUID.randomUUID(), OffsetDateTime.now(), idViaje, UUID.randomUUID(),
                idViaje, XELA, BUS_XELA, XELA, CHOFER_XELA, salida, llegada, UUID.randomUUID());

        rabbitTemplate.convertAndSend("transporte.eventos", rutaDe("llegada"), sobre);

        esperar(() -> jdbc.queryForObject(
                "SELECT count(*) FROM gasto_combustible WHERE id_viaje = ?", Integer.class, idViaje) == 1, 15);
        esperar(() -> jdbc.queryForObject(
                "SELECT count(*) FROM registro_depreciacion WHERE id_viaje = ?", Integer.class, idViaje) == 1, 15);
        esperar(() -> jdbc.queryForObject(
                "SELECT count(*) FROM pago_chofer WHERE id_viaje = ?", Integer.class, idViaje) == 1, 15);

        // Combustible: monto del evento
        assertThat(jdbc.queryForObject(
                "SELECT monto FROM gasto_combustible WHERE id_viaje = ?",
                java.math.BigDecimal.class, idViaje)).isEqualByComparingTo("420.00");

        // Depreciación: 205.5 × tarifa vigente en la fecha de llegada
        java.math.BigDecimal tarifaEsperada = jdbc.queryForObject(
                "SELECT monto_por_km FROM configuracion_depreciacion WHERE vigente_desde <= CURRENT_DATE "
                        + "ORDER BY vigente_desde DESC LIMIT 1", java.math.BigDecimal.class);
        assertThat(jdbc.queryForObject(
                "SELECT monto FROM registro_depreciacion WHERE id_viaje = ?",
                java.math.BigDecimal.class, idViaje))
                .isEqualByComparingTo(new java.math.BigDecimal("205.5")
                        .multiply(tarifaEsperada).setScale(2, java.math.RoundingMode.HALF_UP));
        assertThat(jdbc.queryForObject(
                "SELECT monto_por_km FROM registro_depreciacion WHERE id_viaje = ?",
                java.math.BigDecimal.class, idViaje)).isEqualByComparingTo(tarifaEsperada);

        // Salario regular: 150.00 × 1.00 = 150.00
        assertThat(jdbc.queryForObject(
                "SELECT monto FROM pago_chofer WHERE id_viaje = ?",
                java.math.BigDecimal.class, idViaje)).isEqualByComparingTo("150.00");
        assertThat(jdbc.queryForObject(
                "SELECT multiplicador FROM pago_chofer WHERE id_viaje = ?",
                java.math.BigDecimal.class, idViaje)).isEqualByComparingTo("1.00");

        // Resumen vía API
        mockMvc.perform(conToken(get("/costos/viajes/" + idViaje), tokenAdminSistema()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idViaje").value(idViaje.toString()))
                .andExpect(jsonPath("$.combustible.monto").value(420.00))
                .andExpect(jsonPath("$.pagoChofer.monto").value(150.00))
                .andExpect(jsonPath("$.depreciacion.monto").value(notNullValue()));
    }

    @Test
    void llegadaAlquilerPrivadoUsaMultiplicador115() throws Exception {
        sembrarSucursalYBus();

        UUID idViaje = UUID.randomUUID();
        OffsetDateTime llegada = OffsetDateTime.now().minusHours(1);

        String sobre = """
                {"idEvento":"%s","tipoEvento":"LlegadaRegistrada","versionEvento":1,
                 "ocurridoEn":"%s","servicioProductor":"servicio-viajes",
                 "idAgregado":"%s","versionAgregado":1,"idCorrelacion":"%s",
                 "datos":{"idViaje":"%s","tipoViaje":"ALQUILER_PRIVADO","idAlquiler":"%s",
                          "idRuta":null,"idSucursalOrigen":"%s","idBus":"%s","idSucursalBus":"%s",
                          "idChofer":"%s","salarioBaseChofer":"200.00",
                          "fechaHoraSalidaReal":"%s","fechaHoraLlegadaReal":"%s",
                          "kilometrajeInicial":50000.0,"kilometrajeFinal":50100.0,
                          "distanciaRecorridaKm":100.0,"gastoCombustibleTotal":"200.00",
                          "idUsuarioRegistro":"%s"}}
                """.formatted(UUID.randomUUID(), OffsetDateTime.now(), idViaje, UUID.randomUUID(),
                idViaje, UUID.randomUUID(), XELA, BUS_XELA, XELA, CHOFER_XELA,
                llegada.minusHours(3), llegada, UUID.randomUUID());

        rabbitTemplate.convertAndSend("transporte.eventos", rutaDe("llegada"), sobre);

        esperar(() -> jdbc.queryForObject(
                "SELECT count(*) FROM pago_chofer WHERE id_viaje = ?", Integer.class, idViaje) == 1, 15);

        // 200 × 1.15 = 230.00
        assertThat(jdbc.queryForObject(
                "SELECT monto FROM pago_chofer WHERE id_viaje = ?",
                java.math.BigDecimal.class, idViaje)).isEqualByComparingTo("230.00");
        assertThat(jdbc.queryForObject(
                "SELECT multiplicador FROM pago_chofer WHERE id_viaje = ?",
                java.math.BigDecimal.class, idViaje)).isEqualByComparingTo("1.15");
    }

    @Test
    void busRegistradoProyectaBusRef() throws Exception {
        UUID idBus = UUID.randomUUID();
        String sobre = """
                {"idEvento":"%s","tipoEvento":"BusRegistrado","versionEvento":1,
                 "ocurridoEn":"%s","servicioProductor":"servicio-flota",
                 "idAgregado":"%s","versionAgregado":1,"idCorrelacion":"%s",
                 "datos":{"idBus":"%s","idSucursal":"%s","placa":"P999XYZ",
                          "marca":"Toyota","modelo":"Hiace","anioFabricacion":2022,
                          "capacidadPasajeros":30,"estadoOperativo":"DISPONIBLE","activo":true}}
                """.formatted(UUID.randomUUID(), OffsetDateTime.now(), idBus, UUID.randomUUID(),
                idBus, XELA);

        rabbitTemplate.convertAndSend("transporte.eventos", rutaDe("bus.registrado"), sobre);

        esperar(() -> jdbc.queryForObject(
                "SELECT count(*) FROM bus_ref WHERE id = ?", Integer.class, idBus) == 1, 15);
        assertThat(jdbc.queryForObject(
                "SELECT placa FROM bus_ref WHERE id = ?", String.class, idBus)).isEqualTo("P999XYZ");
    }

    @Test
    void sucursalCreadaProyectaSucursalRef() throws Exception {
        UUID idSucursal = UUID.randomUUID();
        String sobre = """
                {"idEvento":"%s","tipoEvento":"SucursalCreada","versionEvento":1,
                 "ocurridoEn":"%s","servicioProductor":"servicio-identidad",
                 "idAgregado":"%s","versionAgregado":1,"idCorrelacion":"%s",
                 "datos":{"idSucursal":"%s","nombre":"Retalhuleu","departamento":"Retalhuleu",
                          "direccion":"Centro","latitud":14.5,"longitud":-91.6,"activa":true}}
                """.formatted(UUID.randomUUID(), OffsetDateTime.now(), idSucursal, UUID.randomUUID(),
                idSucursal);

        rabbitTemplate.convertAndSend("transporte.eventos", rutaDe("sucursal.creada"), sobre);

        esperar(() -> jdbc.queryForObject(
                "SELECT count(*) FROM sucursal_ref WHERE id = ?", Integer.class, idSucursal) == 1, 15);
        assertThat(jdbc.queryForObject(
                "SELECT nombre FROM sucursal_ref WHERE id = ?", String.class, idSucursal))
                .isEqualTo("Retalhuleu");
    }

    @Test
    void llegadaDuplicadaNoDuplicaCostos() throws Exception {
        sembrarSucursalYBus();

        UUID idViaje = UUID.randomUUID();
        OffsetDateTime llegada = OffsetDateTime.now().minusHours(1);
        String sobre = """
                {"idEvento":"%s","tipoEvento":"LlegadaRegistrada","versionEvento":1,
                 "ocurridoEn":"%s","servicioProductor":"servicio-viajes",
                 "idAgregado":"%s","versionAgregado":1,"idCorrelacion":"%s",
                 "datos":{"idViaje":"%s","tipoViaje":"REGULAR","idAlquiler":null,"idRuta":null,
                          "idSucursalOrigen":"%s","idBus":"%s","idSucursalBus":"%s",
                          "idChofer":"%s","salarioBaseChofer":"100.00",
                          "fechaHoraSalidaReal":"%s","fechaHoraLlegadaReal":"%s",
                          "kilometrajeInicial":1.0,"kilometrajeFinal":51.0,
                          "distanciaRecorridaKm":50.0,"gastoCombustibleTotal":"80.00",
                          "idUsuarioRegistro":"%s"}}
                """.formatted(UUID.randomUUID(), OffsetDateTime.now(), idViaje, UUID.randomUUID(),
                idViaje, XELA, BUS_XELA, XELA, CHOFER_XELA, llegada.minusHours(2), llegada,
                UUID.randomUUID());

        rabbitTemplate.convertAndSend("transporte.eventos", rutaDe("llegada"), sobre);
        esperar(() -> jdbc.queryForObject(
                "SELECT count(*) FROM gasto_combustible WHERE id_viaje = ?", Integer.class, idViaje) == 1, 15);

        // Mismo idViaje con otro idEvento: la deduplicación de evento_procesado + UNIQUE(id_viaje) lo evitan
        String duplicado = sobre.replaceFirst("\"idEvento\":\"[^\"]+\"",
                "\"idEvento\":\"" + UUID.randomUUID() + "\"");
        rabbitTemplate.convertAndSend("transporte.eventos", rutaDe("llegada"), duplicado);
        Thread.sleep(1000);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM gasto_combustible WHERE id_viaje = ?", Integer.class, idViaje))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pago_chofer WHERE id_viaje = ?", Integer.class, idViaje))
                .isEqualTo(1);
    }
}
