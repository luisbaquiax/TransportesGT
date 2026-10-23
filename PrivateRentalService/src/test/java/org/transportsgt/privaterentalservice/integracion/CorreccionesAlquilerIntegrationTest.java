package org.transportsgt.privaterentalservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Correcciones de la evaluación de Alquileres: perfil por evento, rechazos de Cartera, tarifas, sinAsignar,
 * NIT normalizado, versiones de eventos, validaciones, días del precio y fechas de Guatemala.
 */
class CorreccionesAlquilerIntegrationTest extends IntegracionBase {

    private static final ZoneOffset GUATEMALA = ZoneOffset.ofHours(-6);

    private String clienteConPerfil(String nit) {
        UUID idUsuario = UUID.randomUUID();
        jdbc.update("INSERT INTO cliente_ref (id_usuario, nit, nombre_completo, activo, version) "
                + "VALUES (?, ?, 'Correcciones Prueba', true, 0)", idUsuario, nit);
        return tokenClienteConPerfil(idUsuario, null);
    }

    private String mostrador(String cuerpoSolicitud, String nit, String nombre) throws Exception {
        MvcResult r = mockMvc.perform(conToken(post("/alquileres/mostrador"), tokenCajero())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"solicitud":%s,"nitCliente":%s,"nombreCliente":"%s"}"""
                                .formatted(cuerpoSolicitud, nit == null ? "null" : "\"" + nit + "\"", nombre)))
                .andExpect(status().isCreated())
                .andReturn();
        return leer(r).get("id").asString();
    }

    private String solicitud(OffsetDateTime salida) {
        return """
                {"idSucursalOrigen":"%s","origen":"Quetzaltenango","destino":"Retalhuleu",
                 "fechaHoraSalida":"%s","idaYVuelta":false,"numeroPasajeros":20,"distanciaEstimadaKm":"80.00"}"""
                .formatted(XELA, salida);
    }

    private void pagarEnMostrador(String id) throws Exception {
        mockMvc.perform(conToken(post("/alquileres/%s/pago-mostrador".formatted(id)), tokenCajero())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaPago\":\"%s\"}".formatted(LocalDate.now())))
                .andExpect(status().isOk());
    }

    private String sobreViaje(String tipo, UUID idViaje, long version, UUID idAlquiler, String bus, String chofer) {
        return """
                {"idEvento":"%s","tipoEvento":"%s","versionEvento":1,
                 "ocurridoEn":"2026-10-17T12:00:00Z","servicioProductor":"servicio-viajes",
                 "idAgregado":"%s","versionAgregado":%d,"idCorrelacion":"%s",
                 "datos":{"idViaje":"%s","tipoViaje":"ALQUILER_PRIVADO","estado":"PROGRAMADO",
                          "idAlquiler":"%s","nombreOrigen":"X","nombreDestino":"Y",
                          "fechaHoraSalida":"%s","bus":%s,"chofer":%s}}
                """.formatted(UUID.randomUUID(), tipo, idViaje, version, UUID.randomUUID(), idViaje, idAlquiler,
                OffsetDateTime.now().plusDays(5), bus, chofer);
    }

    // ---------- 1. PerfilClienteActualizado ----------

    @Test
    void perfilClienteActualizadoHabilitaLaSolicitudEnLinea() throws Exception {
        UUID idUsuario = UUID.randomUUID();
        String perfil = """
                {"idEvento":"%s","tipoEvento":"PerfilClienteActualizado","versionEvento":1,
                 "ocurridoEn":"2026-10-17T12:00:00Z","servicioProductor":"servicio-cartera",
                 "idAgregado":"%s","versionAgregado":2,"idCorrelacion":"%s",
                 "datos":{"idUsuario":"%s","nit":"5555555-k","nombreCompleto":"Lucía Ramírez","activo":true}}
                """.formatted(UUID.randomUUID(), idUsuario, UUID.randomUUID(), idUsuario);
        rabbitTemplate.convertAndSend("transporte.eventos", rutaDe("cliente.actualizado"), perfil);

        esperar(() -> jdbc.queryForObject("SELECT count(*) FROM cliente_ref WHERE id_usuario = ?",
                Integer.class, idUsuario) == 1, 15);
        assertThat(jdbc.queryForObject("SELECT nit FROM cliente_ref WHERE id_usuario = ?", String.class, idUsuario))
                .isEqualTo("5555555K");

        String id = solicitar(tokenClienteConPerfil(idUsuario, null));
        assertThat(jdbc.queryForObject("SELECT nit_cliente FROM alquiler WHERE id = ?::uuid", String.class, id))
                .isEqualTo("5555555K");
    }

    // ---------- 2. Cartera ----------

    @Test
    void rechazoDeCarteraSeDevuelveTalCualYNoConfirma() throws Exception {
        String token = clienteConPerfil("4444444");
        String id = solicitar(token);
        cotizar(id, tokenAdminSucursal());
        CARTERA.responder(UUID.fromString(id), 422,
                "{\"status\":422,\"error\":\"Saldo insuficiente en la cartera\",\"codigo\":\"SALDO_INSUFICIENTE\"}");

        mockMvc.perform(conToken(post("/alquileres/%s/pago".formatted(id)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaPago\":\"%s\"}".formatted(LocalDate.now())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("SALDO_INSUFICIENTE"))
                .andExpect(jsonPath("$.error").value("Saldo insuficiente en la cartera"));

        assertThat(estadoDe(UUID.fromString(id))).isEqualTo("COTIZADO");
    }

    @Test
    void pagoEnLineaConfirmaYElEventoLlevaLaVersionGuardada() throws Exception {
        String token = clienteConPerfil("3333333");
        String id = solicitar(token);
        cotizar(id, tokenAdminSucursal());
        UUID idAlquiler = UUID.fromString(id);
        UUID idPago = UUID.randomUUID();
        CARTERA.responder(idAlquiler, 201, "{\"idPago\":\"%s\"}".formatted(idPago));

        mockMvc.perform(conToken(post("/alquileres/%s/pago".formatted(id)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaPago\":\"%s\"}".formatted(LocalDate.now())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"))
                .andExpect(jsonPath("$.idPago").value(idPago.toString()));

        Long version = jdbc.queryForObject("SELECT version FROM alquiler WHERE id = ?", Long.class, idAlquiler);
        Long versionEvento = jdbc.queryForObject("SELECT version_agregado FROM outbox_evento "
                + "WHERE id_agregado = ? AND tipo_evento = 'AlquilerConfirmado'", Long.class, idAlquiler);
        assertThat(versionEvento).isEqualTo(version);
        // Solicitado, cotizado y confirmado: versiones distintas y crecientes
        assertThat(jdbc.queryForList("SELECT version_agregado FROM outbox_evento WHERE id_agregado = ? "
                + "ORDER BY creado_en", Long.class, idAlquiler)).doesNotHaveDuplicates().isSorted();
    }

    @Test
    void alquilerSolicitadoLlevaFechaHoraSalida() throws Exception {
        String id = solicitar(clienteConPerfil("2222222"));
        String salida = jdbc.queryForObject("SELECT datos->>'fechaHoraSalida' FROM outbox_evento "
                + "WHERE id_agregado = ?::uuid AND tipo_evento = 'AlquilerSolicitado'", String.class, id);
        assertThat(salida).isNotBlank();
    }

    // ---------- 3. Tarifas ----------

    @Test
    void administradorDeSistemaRegistraYReemplazaTarifas() throws Exception {
        String sis = tokenAdminSistema();
        String fecha = "2031-03-01";
        mockMvc.perform(conToken(post("/alquileres/tarifas"), sis).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"precioPorKm\":\"11.00\",\"precioPorDia\":\"550.00\",\"vigenteDesde\":\"%s\"}"
                                .formatted(fecha)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vigenteDesde").value(fecha));
        mockMvc.perform(conToken(post("/alquileres/tarifas"), sis).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"precioPorKm\":\"12.00\",\"precioPorDia\":\"600.00\",\"vigenteDesde\":\"%s\"}"
                                .formatted(fecha)))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM tarifa_alquiler WHERE vigente_desde = ?::date",
                Integer.class, fecha)).isEqualTo(1);
        mockMvc.perform(conToken(get("/alquileres/tarifas/vigente").param("fecha", "2031-03-02"), tokenCliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precioPorKm").value(12.00));
        mockMvc.perform(conToken(get("/alquileres/tarifas"), sis))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].vigenteDesde", hasItem(fecha)));
        mockMvc.perform(conToken(get("/alquileres/tarifas/vigente").param("fecha", "2020-01-01"), tokenCliente()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SIN_TARIFA"));
        mockMvc.perform(conToken(post("/alquileres/tarifas"), sis).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"precioPorKm\":\"1.00\",\"precioPorDia\":\"1.00\",\"vigenteDesde\":\"2020-01-01\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- 4. sinAsignar ----------

    @Test
    void sinAsignarListaConfirmadosSinBus() throws Exception {
        String conBus = mostrador(solicitud(OffsetDateTime.now().plusDays(4)), null, "Con Bus");
        String sinBus = mostrador(solicitud(OffsetDateTime.now().plusDays(4)), null, "Sin Bus");
        for (String id : new String[]{conBus, sinBus}) {
            cotizar(id, tokenAdminSucursal());
            pagarEnMostrador(id);
        }
        // Ya tiene viaje (ViajeProgramado) pero aún sin bus: sigue por asignar
        jdbc.update("UPDATE alquiler SET id_viaje = gen_random_uuid() WHERE id = ?::uuid", sinBus);
        jdbc.update("UPDATE alquiler SET id_viaje = gen_random_uuid(), id_bus = gen_random_uuid(), "
                + "placa_bus = 'P123ABC' WHERE id = ?::uuid", conBus);

        mockMvc.perform(conToken(get("/alquileres").param("sinAsignar", "true").param("tamano", "100"),
                        tokenAdminSucursal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[*].id", hasItem(sinBus)))
                .andExpect(jsonPath("$.contenido[*].id", not(hasItem(conBus))));
    }

    // ---------- 5. NIT normalizado ----------

    @Test
    void mostradorConNitConGuionApareceEnElHistorialYElDetalleDelCliente() throws Exception {
        String id = mostrador(solicitud(OffsetDateTime.now().plusDays(6)), "9876543-k", "Mario Gómez");
        assertThat(jdbc.queryForObject("SELECT nit_cliente FROM alquiler WHERE id = ?::uuid", String.class, id))
                .isEqualTo("9876543K");
        String token = clienteConPerfil("9876543K");

        mockMvc.perform(conToken(get("/alquileres/mios"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[*].id", hasItem(id)));
        mockMvc.perform(conToken(get("/alquileres/" + id), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canal").value("MOSTRADOR"));
        mockMvc.perform(conToken(get("/alquileres/" + id), clienteConPerfil("1111111")))
                .andExpect(status().isForbidden());
    }

    @Test
    void nitConCaracteresInvalidosEs400() throws Exception {
        mockMvc.perform(conToken(post("/alquileres/mostrador"), tokenCajero())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"solicitud\":%s,\"nitCliente\":\"12.34\",\"nombreCliente\":\"X\"}"
                                .formatted(solicitud(OffsetDateTime.now().plusDays(6)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("NIT_INVALIDO"));
    }

    // ---------- 6. versión de ViajeActualizado ----------

    @Test
    void viajeActualizadoAtrasadoNoPisaElBus() throws Exception {
        String id = mostrador(solicitud(OffsetDateTime.now().plusDays(8)), null, "Versiones");
        cotizar(id, tokenAdminSucursal());
        pagarEnMostrador(id);
        UUID idAlquiler = UUID.fromString(id);
        UUID idViaje = UUID.randomUUID();
        String ruta = rutaDe("viaje.actualizado");

        rabbitTemplate.convertAndSend("transporte.eventos", ruta, sobreViaje("ViajeActualizado", idViaje, 3,
                idAlquiler, "{\"idBus\":\"%s\",\"placa\":\"P300AAA\",\"capacidad\":40}".formatted(UUID.randomUUID()),
                "null"));
        esperar(() -> "P300AAA".equals(jdbc.queryForObject(
                "SELECT placa_bus FROM alquiler WHERE id = ?", String.class, idAlquiler)), 15);
        // Llega tarde la v2 con otro bus y luego la v4 que solo trae el chofer
        rabbitTemplate.convertAndSend("transporte.eventos", ruta, sobreViaje("ViajeActualizado", idViaje, 2,
                idAlquiler, "{\"idBus\":\"%s\",\"placa\":\"P200BBB\",\"capacidad\":40}".formatted(UUID.randomUUID()),
                "null"));
        rabbitTemplate.convertAndSend("transporte.eventos", ruta, sobreViaje("ViajeActualizado", idViaje, 4,
                idAlquiler, "null", "{\"idChofer\":\"%s\",\"nombreCompleto\":\"Pedro Chofer\"}"
                        .formatted(UUID.randomUUID())));
        esperar(() -> "Pedro Chofer".equals(jdbc.queryForObject(
                "SELECT nombre_chofer FROM alquiler WHERE id = ?", String.class, idAlquiler)), 15);

        assertThat(jdbc.queryForObject("SELECT placa_bus FROM alquiler WHERE id = ?", String.class, idAlquiler))
                .isEqualTo("P300AAA");
        assertThat(jdbc.queryForObject("SELECT version_viaje FROM alquiler WHERE id = ?", Long.class, idAlquiler))
                .isEqualTo(4L);
    }

    // ---------- 7. validaciones ----------

    @Test
    void validacionesDeSolicitudYCotizacion() throws Exception {
        String token = clienteConPerfil("7654321");
        mockMvc.perform(conToken(post("/alquileres/solicitudes"), token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idSucursalOrigen":"%s","origen":"X","destino":"Y","fechaHoraSalida":"%s",
                                 "numeroPasajeros":10,"latitudOrigen":14.8,"longitudOrigen":-91.5}"""
                                .formatted(XELA, OffsetDateTime.now().plusDays(5))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("COORDENADAS_INCOMPLETAS"));
        mockMvc.perform(conToken(post("/alquileres/solicitudes"), token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idSucursalOrigen":"%s","origen":"X","destino":"Y","fechaHoraSalida":"%s",
                                 "numeroPasajeros":10,"latitudOrigen":95,"longitudOrigen":-91.5,
                                 "latitudDestino":14.6,"longitudDestino":-90.5}"""
                                .formatted(XELA, OffsetDateTime.now().plusDays(5))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("COORDENADAS_INVALIDAS"));

        // Mostrador: nombre obligatorio y solicitud anidada obligatoria
        mockMvc.perform(conToken(post("/alquileres/mostrador"), tokenCajero()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"solicitud\":%s,\"nitCliente\":null}".formatted(solicitud(OffsetDateTime.now().plusDays(5)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
        mockMvc.perform(conToken(post("/alquileres/mostrador"), tokenCajero()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreCliente\":\"X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));

        String id = solicitar(token);
        mockMvc.perform(conToken(post("/alquileres/%s/cotizacion".formatted(id)), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"precioFinal\":\"100.00\",\"fechaHoraLlegadaEstimada\":\"%s\"}"
                                .formatted(OffsetDateTime.now().plusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));

        mockMvc.perform(conToken(get("/alquileres").param("tamano", "500"), tokenAdminSucursal()))
                .andExpect(status().isBadRequest());
    }

    // ---------- 8. días del precio ----------

    @Test
    void idaYVueltaEntreMesesCobraTodosLosDias() throws Exception {
        OffsetDateTime salida = OffsetDateTime.now(GUATEMALA).plusDays(30).truncatedTo(ChronoUnit.DAYS).plusHours(8);
        OffsetDateTime retorno = salida.plusDays(32).plusHours(10);
        MvcResult r = mockMvc.perform(conToken(post("/alquileres/solicitudes"), clienteConPerfil("6543210"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idSucursalOrigen":"%s","origen":"X","destino":"Y","fechaHoraSalida":"%s",
                                 "idaYVuelta":true,"fechaHoraRetorno":"%s","numeroPasajeros":10,
                                 "distanciaEstimadaKm":"100.00"}""".formatted(XELA, salida, retorno)))
                .andExpect(status().isCreated())
                .andReturn();
        // 100 km × 2 × 10.00 + 33 días × 500.00
        assertThat(leer(r).get("precioEstimado").decimalValue()).isEqualByComparingTo("18500.00");
    }

    // ---------- 9. fechas de Guatemala ----------

    @Test
    void filtroPorDiaUsaElDiaDeGuatemala() throws Exception {
        // 23:30 de Guatemala = 05:30 UTC del día siguiente
        OffsetDateTime salida = OffsetDateTime.now(GUATEMALA).plusDays(40)
                .truncatedTo(ChronoUnit.DAYS).plusHours(23).plusMinutes(30);
        String id = mostrador(solicitud(salida), null, "Noche");
        String dia = salida.toLocalDate().toString();

        JsonNode pagina = leer(mockMvc.perform(conToken(get("/alquileres")
                                .param("desde", dia).param("hasta", dia).param("tamano", "100"), tokenAdminSistema()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(pagina.get("contenido").findValuesAsString("id")).contains(id);

        String siguiente = salida.toLocalDate().plusDays(1).toString();
        JsonNode otroDia = leer(mockMvc.perform(conToken(get("/alquileres")
                                .param("desde", siguiente).param("hasta", siguiente).param("tamano", "100"),
                        tokenAdminSistema()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(otroDia.get("contenido").findValuesAsString("id")).doesNotContain(id);
    }
}
