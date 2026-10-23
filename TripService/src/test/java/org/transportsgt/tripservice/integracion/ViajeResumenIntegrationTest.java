package org.transportsgt.tripservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.transportsgt.eventos.payload.Rol;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /viajes/resumen?ids= : datos públicos de varios viajes para el historial de compras de cualquier usuario.
 */
class ViajeResumenIntegrationTest extends IntegracionBase {

    private static final UUID VIAJE_FINALIZADO = UUID.fromString("50000000-0000-4000-8000-000000000002");

    private String tokenCliente() {
        return token(Rol.CLIENTE, null, UUID.randomUUID());
    }

    @Test
    void clienteVeElResumenSinDatosDelChoferNiRegistros() throws Exception {
        mockMvc.perform(get("/viajes/resumen").param("ids", VIAJE_SEMILLA.toString())
                        .header("Authorization", "Bearer " + tokenCliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(VIAJE_SEMILLA.toString()))
                .andExpect(jsonPath("$[0].tipoViaje").value("REGULAR"))
                .andExpect(jsonPath("$[0].estado").value("PROGRAMADO"))
                .andExpect(jsonPath("$[0].idRuta").value(RUTA_XELA_CAPITAL.toString()))
                .andExpect(jsonPath("$[0].nombreOrigen").value("Quetzaltenango"))
                .andExpect(jsonPath("$[0].nombreDestino").value("Ciudad de Guatemala"))
                .andExpect(jsonPath("$[0].fechaHoraSalida").exists())
                // Bus del viaje semilla (V2): b0000000-...-0001
                .andExpect(jsonPath("$[0].placaBus").value("P900ABC"))
                .andExpect(jsonPath("$[0].chofer").doesNotExist())
                .andExpect(jsonPath("$[0].bus").doesNotExist())
                .andExpect(jsonPath("$[0].registroSalida").doesNotExist())
                .andExpect(jsonPath("$[0].registroLlegada").doesNotExist());
    }

    @Test
    void variosIdsEnUnaLlamadaOrdenadosPorSalidaYOmiteInexistentes() throws Exception {
        String ids = String.join(",", VIAJE_SEMILLA.toString(), UUID.randomUUID().toString(),
                VIAJE_FINALIZADO.toString(), VIAJE_SEMILLA.toString());
        mockMvc.perform(get("/viajes/resumen").param("ids", ids)
                        .header("Authorization", "Bearer " + tokenCliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                // El finalizado salió hace 10 días; el programado sale en 7
                .andExpect(jsonPath("$[0].id").value(VIAJE_FINALIZADO.toString()))
                .andExpect(jsonPath("$[0].estado").value("FINALIZADO"))
                .andExpect(jsonPath("$[1].id").value(VIAJE_SEMILLA.toString()));
    }

    @Test
    void cajeroDeOtraSucursalTambienLoVe() throws Exception {
        // GET /viajes/{id} se lo negaría; el resumen no restringe por sucursal
        mockMvc.perform(get("/viajes/resumen").param("ids", VIAJE_SEMILLA.toString())
                        .header("Authorization", "Bearer " + token(Rol.CAJERO, CAPITAL, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void viajeEliminadoSeOmite() throws Exception {
        // Lejos en el futuro para no traslaparse con los viajes de otras pruebas (mismo bus y chofer)
        OffsetDateTime salida = OffsetDateTime.now().plusDays(90);
        String id = leer(mockMvc.perform(conToken(post("/viajes"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"idRuta":"%s","idBus":"%s","idChofer":"%s",
                                 "fechaHoraSalida":"%s","fechaHoraLlegadaEstimada":"%s"}"""
                                .formatted(RUTA_XELA_CAPITAL, BUS_2, CHOFER_2, salida, salida.plusHours(4)))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asString();

        mockMvc.perform(get("/viajes/resumen").param("ids", id)
                        .header("Authorization", "Bearer " + tokenCliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].placaBus").value("P456DEF"));

        mockMvc.perform(conToken(delete("/viajes/" + id), tokenAdminSucursal()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/viajes/resumen").param("ids", id)
                        .header("Authorization", "Bearer " + tokenCliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get("/viajes/resumen").param("ids", VIAJE_SEMILLA.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sinIdsResponde400() throws Exception {
        mockMvc.perform(get("/viajes/resumen")
                        .header("Authorization", "Bearer " + tokenCliente()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/viajes/resumen").param("ids", "")
                        .header("Authorization", "Bearer " + tokenCliente()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void idInvalidoResponde400() throws Exception {
        mockMvc.perform(get("/viajes/resumen").param("ids", "no-es-uuid")
                        .header("Authorization", "Bearer " + tokenCliente()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void masDelMaximoResponde400() throws Exception {
        String ids = IntStream.rangeClosed(0, 100).mapToObj(i -> UUID.randomUUID().toString())
                .collect(Collectors.joining(","));
        mockMvc.perform(get("/viajes/resumen").param("ids", ids)
                        .header("Authorization", "Bearer " + tokenCliente()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }
}
