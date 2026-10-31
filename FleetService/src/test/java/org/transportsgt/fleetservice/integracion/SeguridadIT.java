package org.transportsgt.fleetservice.integracion;

import org.junit.jupiter.api.Test;
import org.transportsgt.eventos.payload.identidad.Rol;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Puerta de entrada obligatoria: 401 sin/basura, 403 por rol, rutas públicas y
 * reglas de seguridad de Spring Security (POST /buses solo ADMIN_SUCURSAL).
 */
class SeguridadIT extends IntegracionBase {

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/buses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void tokenBasuroDevuelve401() throws Exception {
        mockMvc.perform(get("/buses").header("Authorization", "Bearer token-inventado"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void rolClienteNoPuedeVerFlota() throws Exception {
        mockMvc.perform(conToken(get("/buses"), token(Rol.CLIENTE, XELA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    void adminSistemaNoCreaBusesSoloConsulta() throws Exception {
        mockMvc.perform(conToken(post("/buses"), tokenAdminSistema())
                        .contentType("application/json")
                        .content(json("""
                                {"placa":"P-NO-DEBE","marca":"Toyota","modelo":"Hiace","anioFabricacion":2020,
                                 "capacidadPasajeros":40}""")))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(get("/buses"), tokenAdminSistema()))
                .andExpect(status().isOk());
    }

    @Test
    void fotosNoSonRutaPublica() throws Exception {
        // Las fotos son privadas: sin token se rechaza antes de buscar el archivo
        mockMvc.perform(get("/buses/" + java.util.UUID.randomUUID() + "/foto"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/choferes/" + java.util.UUID.randomUUID() + "/foto"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSinSucursalNoPuedeGestionar() throws Exception {
        mockMvc.perform(conToken(post("/buses"), token(Rol.ADMIN_SUCURSAL, null))
                        .contentType("application/json")
                        .content(json("""
                                {"placa":"P-SIN-SUCURSAL","marca":"Toyota","modelo":"Hiace","anioFabricacion":2020,
                                 "capacidadPasajeros":40}""")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("OPERACION_NO_AUTORIZADA"));
    }

    @Test
    void cuerpoNoJsonDevuelve400() throws Exception {
        mockMvc.perform(conToken(post("/buses"), tokenAdminSucursal())
                        .contentType("application/json")
                        .content("esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    void tipoDeParametroInvalidoDevuelve400() throws Exception {
        mockMvc.perform(conToken(get("/buses?idSucursal=abc"), tokenAdminSistema()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }
}
