package org.transportsgt.tripservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.transportsgt.eventos.payload.Rol;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato de eventos (catálogo §2.3) y permisos (plan §3.3): versión correcta de cada evento, ViajeProgramado al
 * crear, ViajeActualizado al salir y llegar, lectura del cajero y republicación de rutas y viajes.
 */
class CorreccionesViajeIntegrationTest extends IntegracionBase {

    private String crearViaje(UUID idBus, UUID idChofer, OffsetDateTime salida, OffsetDateTime llegada) throws Exception {
        return leer(mockMvc.perform(conToken(post("/viajes"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"idRuta":"%s","idBus":"%s","idChofer":"%s",
                                 "fechaHoraSalida":"%s","fechaHoraLlegadaEstimada":"%s"}"""
                                .formatted(RUTA_XELA_CAPITAL, idBus, idChofer, salida, llegada))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asString();
    }

    /** Filas del outbox del agregado como "TipoEvento@version[estado]", para comparar sin depender del orden. */
    private List<String> eventosDe(String idAgregado) {
        return jdbc.query("""
                        SELECT tipo_evento, version_agregado, datos ->> 'estado' AS estado
                          FROM outbox_evento WHERE id_agregado = ?""",
                (fila, n) -> fila.getString("tipo_evento") + "@" + fila.getLong("version_agregado")
                        + (fila.getString("estado") == null ? "" : "[" + fila.getString("estado") + "]"),
                UUID.fromString(idAgregado));
    }

    @Test
    void cadaEventoLlevaLaVersionActualDelViaje() throws Exception {
        OffsetDateTime salida = OffsetDateTime.now().plusDays(100);
        String id = crearViaje(BUS_2, CHOFER_2, salida, salida.plusHours(4));

        mockMvc.perform(conToken(put("/viajes/" + id), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"fechaHoraSalida":"%s","fechaHoraLlegadaEstimada":"%s"}"""
                                .formatted(salida.plusHours(1), salida.plusHours(6)))))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(post("/viajes/%s/salida".formatted(id)), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"fechaHoraSalidaReal":"%s","kilometrajeInicial":1000}""".formatted(salida))))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(post("/viajes/%s/llegada".formatted(id)), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"fechaHoraLlegadaReal":"%s","kilometrajeFinal":1205.5,"gastoCombustibleTotal":420}"""
                                .formatted(salida.plusHours(5)))))
                .andExpect(status().isOk());

        Long version = jdbc.queryForObject("SELECT version FROM viaje WHERE id = ?", Long.class, UUID.fromString(id));
        assertThat(version).isEqualTo(3L);
        assertThat(eventosDe(id)).containsExactlyInAnyOrder(
                "ViajeProgramado@0[PROGRAMADO]",
                "ViajeActualizado@1[PROGRAMADO]",
                "SalidaRegistrada@2",
                "ViajeActualizado@2[EN_TRANSITO]",
                "LlegadaRegistrada@3",
                "ViajeActualizado@3[FINALIZADO]");
    }

    @Test
    void traslapeAlActualizarResponde409() throws Exception {
        OffsetDateTime salida = OffsetDateTime.now().plusDays(110);
        crearViaje(BUS_2, CHOFER_2, salida, salida.plusHours(4));
        String segundo = crearViaje(BUS_2, CHOFER_1, salida.plusHours(10), salida.plusHours(14));

        // Mover el segundo viaje encima del primero (mismo bus)
        mockMvc.perform(conToken(put("/viajes/" + segundo), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"fechaHoraSalida":"%s","fechaHoraLlegadaEstimada":"%s"}"""
                                .formatted(salida.plusHours(1), salida.plusHours(5)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_HORARIO"));
    }

    @Test
    void rutaActualizadaLlevaLaVersionNueva() throws Exception {
        UUID destino = UUID.randomUUID();
        jdbc.update("INSERT INTO sucursal_ref (id, nombre, activa, version) VALUES (?, 'Mazatenango', true, 0)", destino);
        String id = leer(mockMvc.perform(conToken(post("/rutas"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"idSucursalOrigen":"%s","idSucursalDestino":"%s",
                                 "distanciaKm":"75.00","precioBoleto":"35.00"}""".formatted(XELA, destino))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asString();
        mockMvc.perform(conToken(put("/rutas/" + id), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"idSucursalOrigen":"%s","idSucursalDestino":"%s",
                                 "distanciaKm":"80.00","precioBoleto":"40.00"}""".formatted(XELA, destino))))
                .andExpect(status().isOk());

        assertThat(eventosDe(id)).containsExactlyInAnyOrder("RutaCreada@0", "RutaActualizada@1");
    }

    @Test
    void cajeroConsultaLosViajesDeSuSucursalSinModificarlos() throws Exception {
        mockMvc.perform(get("/viajes").header("Authorization", "Bearer " + tokenCajero()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[*].id", hasItem(VIAJE_SEMILLA.toString())));
        mockMvc.perform(get("/viajes/" + VIAJE_SEMILLA).header("Authorization", "Bearer " + tokenCajero()))
                .andExpect(status().isOk());
        // Cajero de otra sucursal
        mockMvc.perform(get("/viajes/" + VIAJE_SEMILLA)
                        .header("Authorization", "Bearer " + token(Rol.CAJERO, CAPITAL, null)))
                .andExpect(status().isForbidden());
        // Solo lectura
        mockMvc.perform(conToken(post("/viajes"), tokenCajero())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void republicarEventosSoloAdminSistema() throws Exception {
        int antes = jdbc.queryForObject("""
                SELECT count(*) FROM outbox_evento WHERE id_agregado = ? AND tipo_evento = 'ViajeProgramado'""",
                Integer.class, VIAJE_SEMILLA);

        mockMvc.perform(conToken(post("/admin/republicar-eventos"), tokenAdminSistema()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rutas").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.viajes").value(greaterThanOrEqualTo(3)));

        int despues = jdbc.queryForObject("""
                SELECT count(*) FROM outbox_evento WHERE id_agregado = ? AND tipo_evento = 'ViajeProgramado'""",
                Integer.class, VIAJE_SEMILLA);
        assertThat(despues).isEqualTo(antes + 1);
        // Solo instantáneas: los hechos del viaje finalizado no se republican
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM outbox_evento WHERE tipo_evento IN ('SalidaRegistrada', 'LlegadaRegistrada')
                   AND id_agregado = '50000000-0000-4000-8000-000000000002'""", Integer.class)).isZero();

        mockMvc.perform(conToken(post("/admin/republicar-eventos"), tokenAdminSucursal()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/republicar-eventos"))
                .andExpect(status().isUnauthorized());
    }
}
