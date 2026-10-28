package org.transportsgt.tripservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeguridadIT extends IntegracionBase {

    @Test
    void sinTokenEs401() throws Exception {
        mockMvc.perform(get("/viajes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/rutas")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/viajes/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/viajes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/viajes/%s/salida".formatted(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clienteNoPuedeNiLeerNiEscribir() throws Exception {
        String tokenCliente = token(org.transportsgt.eventos.payload.identidad.Rol.CLIENTE, null, null);
        // GET /rutas es solo "authenticated": el cliente sí puede ver el catálogo
        mockMvc.perform(get("/rutas")
                        .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isOk());
        // GET /viajes y POST /viajes exigen rol de operación
        mockMvc.perform(get("/viajes")
                        .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/viajes")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/rutas")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void caJeroNoEscribeViajes() throws Exception {
        mockMvc.perform(conToken(post("/viajes"), tokenCajero())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(post("/viajes"), tokenChofer1())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void viajesActivosRequiereClaveInterna() throws Exception {
        UUID idBus = UUID.randomUUID();
        // Sin clave → 401
        mockMvc.perform(get("/viajes/activos").param("idBus", idBus.toString()))
                .andExpect(status().isUnauthorized());
        // Clave incorrecta → 401
        mockMvc.perform(get("/viajes/activos").param("idBus", idBus.toString())
                        .header("X-Clave-Interna", "clave-mala"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        // Clave correcta → 200 (lista vacía)
        mockMvc.perform(get("/viajes/activos").param("idBus", idBus.toString())
                        .header("X-Clave-Interna", CLAVE_INTERNA_PRUEBAS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        // Con Authorization, la clave no aplica → 401 por no traer token válido
        mockMvc.perform(get("/viajes/activos").param("idBus", idBus.toString())
                        .header("X-Clave-Interna", CLAVE_INTERNA_PRUEBAS)
                        .header("Authorization", "Bearer token-falso"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeOtraFirmaEs401() throws Exception {
        mockMvc.perform(get("/viajes")
                        .header("Authorization", "Bearer token-falso"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value(anything()));
    }

    @Test
    void swaggerYHealthSonPublicos() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    private static org.hamcrest.Matcher<Object> anything() {
        return org.hamcrest.Matchers.anything();
    }

    @Test
    void validacionDeCuerpoDevuelve400() throws Exception {
        mockMvc.perform(conToken(post("/viajes"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"idRuta":null,"idBus":null,"idChofer":null,
                                 "fechaHoraSalida":null,"fechaHoraLlegadaEstimada":null}""")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    @Test
    void viajeInexistenteEs404() throws Exception {
        mockMvc.perform(get("/viajes/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenAdminSistema()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("VIAJE_NO_ENCONTRADO"));
    }
}
