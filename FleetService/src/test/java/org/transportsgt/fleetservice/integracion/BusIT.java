package org.transportsgt.fleetservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.transportsgt.eventos.payload.identidad.Rol;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.everyItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integración HTTP + PostgreSQL: CRUD de buses con semilla V2, desactivación
 * condicionada (fail-closed contra Viajes), triggers de auditoría y eventos.
 */
class BusIT extends IntegracionBase {

    @Test
    void listarTraeSemilla() throws Exception {
        mockMvc.perform(conToken(get("/buses"), tokenAdminSistema()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos", greaterThanOrEqualTo(9)))
                .andExpect(jsonPath("$.contenido[?(@.placa=='" + PLACA_SEMILLA + "')]").exists());
    }

    @Test
    void filtrarPorEstadoYActivoYTexto() throws Exception {
        mockMvc.perform(conToken(get("/buses?estadoOperativo=EN_TALLER"), tokenAdminSistema()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].placa").value("P907ABC"));

        mockMvc.perform(conToken(get("/buses?activo=false"), tokenAdminSistema()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].placa").value("P905ABC"));

        mockMvc.perform(conToken(get("/buses?texto=P900"), tokenAdminSistema()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].placa").value(PLACA_SEMILLA));
    }

    @Test
    void listarSoloSucursalDelTokenYParametroAjeno403() throws Exception {
        mockMvc.perform(conToken(get("/buses"), tokenAdminSucursal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$.contenido[*].id", everyItem(org.hamcrest.Matchers.notNullValue())));

        mockMvc.perform(conToken(get("/buses"), token(Rol.ADMIN_SUCURSAL, CAPITAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$.contenido[0].idSucursal").value(CAPITAL.toString()));

        // Parametrizar una sucursal ajena a la del token → 403
        mockMvc.perform(conToken(get("/buses?idSucursal=" + HUEHUE), tokenAdminSucursal()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("OPERACION_NO_AUTORIZADA"));
    }

    @Test
    void crearBusRegistraOutbox() throws Exception {
        String placa = unico("PIT");
        MvcResult resultado = mockMvc.perform(conToken(post("/buses"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"placa":"%s","marca":"Mercedes","modelo":"Sprinter","anioFabricacion":2022,
                                 "capacidadPasajeros":18,"kilometrajeActual":5}""".formatted(placa))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placa").value(placa))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.activo").value(true))
                .andReturn();

        UUID id = UUID.fromString(leer(resultado).get("id").asString());
        assertThat(contarOutbox(id, "BusRegistrado")).isEqualTo(1);
        assertThat(placaDe(id)).isEqualTo(placa);
    }

    @Test
    void crearPlacaDuplicadaDevuelve409() throws Exception {
        String placa = unico("PDUP");
        crearBus(placa);
        mockMvc.perform(conToken(post("/buses"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"placa":"%s","marca":"Mercedes","modelo":"Sprinter","anioFabricacion":2022,
                                 "capacidadPasajeros":18}""".formatted(placa))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PLACA_DUPLICADA"));
    }

    @Test
    void obtenerYActualizarPorId() throws Exception {
        String id = crearBus(unico("PGET"));
        UUID idBus = UUID.fromString(id);

        mockMvc.perform(conToken(get("/buses/" + id), tokenAdminSistema()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        String placaActual = placaDe(UUID.fromString(id));
        mockMvc.perform(conToken(put("/buses/" + id), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"placa":"%s","marca":"Toyota","modelo":"Coaster","anioFabricacion":2021,
                                 "capacidadPasajeros":30,"version":0}""".formatted(placaActual))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marca").value("Toyota"))
                .andExpect(jsonPath("$.version").value(1));

        // Versión vieja → conflicto optimista
        mockMvc.perform(conToken(put("/buses/" + id), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"placa":"%s","marca":"Otra","modelo":"X","anioFabricacion":2020,
                                 "capacidadPasajeros":10,"version":0}""".formatted(placaActual))))
                .andExpect(status().isConflict());

        // Recurso inexistente
        mockMvc.perform(conToken(get("/buses/" + UUID.randomUUID()), tokenAdminSistema()))
                .andExpect(status().isNotFound());
        assertThat(kilometrajeDe(idBus)).isNotNull();
    }

    @Test
    void desactivarSinViajesDevuelve503FailClosed() throws Exception {
        String id = crearBus(unico("PDES"));
        // Viajes no responde (puerto 9): la regla falla cerrada
        mockMvc.perform(conToken(patch("/buses/" + id + "/activo"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"activo\":false,\"version\":0}")))
                .andExpect(status().isServiceUnavailable());
        // No se desactivó
        assertThat(jdbc.queryForObject("SELECT activo FROM bus WHERE id = ?", Boolean.class,
                UUID.fromString(id))).isTrue();
    }

    @Test
    void estadoInvalidoDevuelve409() throws Exception {
        String id = crearBus(unico("PEST"));
        // EN_TRANSITO solo lo ponen los eventos de Viajes; la API lo rechaza
        mockMvc.perform(conToken(patch("/buses/" + id + "/estado-operativo"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"estadoOperativo\":\"EN_TRANSITO\",\"version\":0}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
    }

    @Test
    void deleteSqlDisparaTriggerDeAuditoria() throws Exception {
        String id = crearBus(unico("PDEL"));
        String sql = "DELETE FROM bus WHERE id = '" + id + "'";
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> jdbc.execute(sql));
        assertThat(placaDe(UUID.fromString(id))).isNotBlank();
    }

    @Test
    void salidaYLlegadaActualizanEstadoYKilometraje() throws Exception {
        String id = crearBus(unico("PVEJ"));
        UUID idBus = UUID.fromString(id);
        BigDecimal kilometrajeInicial = BigDecimal.valueOf(100);
        jdbc.update("UPDATE bus SET kilometraje_actual = ? WHERE id = ?", kilometrajeInicial, idBus);
        String kmAntes = kilometrajeDe(idBus);

        String rutaSalida = rutaDe("salida");
        UUID idViaje = UUID.randomUUID();
        String sobre = """
                {"idEvento":"%s","tipoEvento":"SalidaRegistrada","versionEvento":1,
                 "ocurridoEn":"2026-10-17T12:00:00Z","servicioProductor":"servicio-viajes",
                 "idAgregado":"%s","versionAgregado":1,"idCorrelacion":"%s",
                 "datos":{"idViaje":"%s","idBus":"%s","idChofer":"%s",
                          "fechaHoraSalidaReal":"2026-10-17T12:00:00-06:00",
                          "kilometrajeInicial":150,"idUsuarioRegistro":"%s"}}
                """.formatted(UUID.randomUUID(), idViaje, UUID.randomUUID(), idViaje, id,
                UUID.fromString(crearChofer(unico("LICV"))), UUID.randomUUID());
        rabbitTemplate.convertAndSend("transporte.eventos", rutaSalida, sobre);
        esperar(() -> "EN_TRANSITO".equals(estadoDe(idBus)), 15);
        assertThat(new BigDecimal(kilometrajeDe(idBus))).isEqualByComparingTo("150");

        String rutaLlegada = rutaDe("llegada");
        String llegada = """
                {"idEvento":"%s","tipoEvento":"LlegadaRegistrada","versionEvento":1,
                 "ocurridoEn":"2026-10-17T18:00:00Z","servicioProductor":"servicio-viajes",
                 "idAgregado":"%s","versionAgregado":2,"idCorrelacion":"%s",
                 "datos":{"idViaje":"%s","idBus":"%s","fechaHoraLlegadaReal":"2026-10-17T18:00:00-06:00",
                          "kilometrajeFinal":520}}
                """.formatted(UUID.randomUUID(), idViaje, UUID.randomUUID(), idViaje, id);
        rabbitTemplate.convertAndSend("transporte.eventos", rutaLlegada, llegada);
        esperar(() -> "DISPONIBLE".equals(estadoDe(idBus)), 15);
        assertThat(new BigDecimal(kilometrajeDe(idBus))).isEqualByComparingTo("520");
        // Monotonía: nunca baja respecto del kilometraje previo
        assertThat(new BigDecimal(kilometrajeDe(idBus)).compareTo(new BigDecimal(kmAntes))).isGreaterThanOrEqualTo(0);
    }
}
