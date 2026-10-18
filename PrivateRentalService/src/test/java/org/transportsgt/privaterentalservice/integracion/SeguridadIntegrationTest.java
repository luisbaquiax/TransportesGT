package org.transportsgt.privaterentalservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.transportsgt.eventos.payload.Rol;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.anything;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeguridadIntegrationTest extends IntegracionBase {

    @Test
    void sinTokenEs401EnTodasLasRutas() throws Exception {
        mockMvc.perform(get("/alquileres")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/alquileres/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/alquileres/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/alquileres/mostrador")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/alquileres/admin/republicar-eventos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clienteNoPuedeListarNiCotizar() throws Exception {
        mockMvc.perform(get("/alquileres")
                        .header("Authorization", "Bearer " + tokenCliente()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/alquileres/" + UUID.randomUUID() + "/cotizacion")
                        .header("Authorization", "Bearer " + tokenCliente())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void mostradorProhibidoParaClienteYAdminSistema() throws Exception {
        mockMvc.perform(post("/alquileres/mostrador")
                        .header("Authorization", "Bearer " + tokenCliente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"solicitud":{"idSucursalOrigen":"%s","origen":"X","destino":"Y",
                                 "fechaHoraSalida":"%s","numeroPasajeros":10,
                                 "distanciaEstimadaKm":"50"}}"""
                                .formatted(XELA, OffsetDateTime.now().plusDays(5)))))
                .andExpect(status().isForbidden());
    }

    @Test
    void republicarSoloAdminSistema() throws Exception {
        mockMvc.perform(post("/alquileres/admin/republicar-eventos")
                        .header("Authorization", "Bearer " + tokenAdminSucursal()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/alquileres/admin/republicar-eventos")
                        .header("Authorization", "Bearer " + tokenCajero()))
                .andExpect(status().isForbidden());
    }

    @Test
    void swaggerYHealthSonPublicos() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void tokenDeOtraFirmaEs401() throws Exception {
        mockMvc.perform(get("/alquileres")
                        .header("Authorization", "Bearer token-falso"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value(anything()));
    }
}
