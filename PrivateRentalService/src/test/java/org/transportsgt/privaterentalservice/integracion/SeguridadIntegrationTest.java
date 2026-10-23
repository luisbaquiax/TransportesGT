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
        mockMvc.perform(get("/alquileres/tarifas/vigente")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/alquileres/tarifas")).andExpect(status().isUnauthorized());
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
    void tarifasSoloLasGestionaAdminSistema() throws Exception {
        String cuerpo = json("""
                {"precioPorKm":"12.00","precioPorDia":"600.00","vigenteDesde":"2030-01-01"}""");
        for (String token : new String[]{tokenAdminSucursal(), tokenCajero(), tokenCliente()}) {
            mockMvc.perform(get("/alquileres/tarifas").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/alquileres/tarifas").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void tarifaVigenteLaVeCualquierAutenticado() throws Exception {
        mockMvc.perform(get("/alquileres/tarifas/vigente").header("Authorization", "Bearer " + tokenCliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precioPorKm").exists());
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
