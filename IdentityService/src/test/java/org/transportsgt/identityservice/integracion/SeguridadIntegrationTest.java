package org.transportsgt.identityservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 401 (sin token o token inválido) y 403 (rol sin permiso) con el mismo JSON del handler.
 */
class SeguridadIntegrationTest extends IntegracionBase {

    @Test
    void sinToken_responde401ConJson() throws Exception {
        mockMvc.perform(get("/usuarios"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.ruta").value("/usuarios"))
                .andExpect(jsonPath("$.timestamp").exists());
        mockMvc.perform(get("/auth/yo")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/cambiar-contrasena").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenInvalido_responde401() throws Exception {
        mockMvc.perform(conToken(get("/sucursales"), "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.firma-falsa"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void cliente_noPuedeAdministrar_403() throws Exception {
        String token = login(CLIENTE, CONTRASENA_CLIENTE);

        mockMvc.perform(conToken(get("/usuarios"), token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        mockMvc.perform(conToken(post("/sucursales"), token).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSucursal_noPuedeCrearSucursalesNiRepublicar_403() throws Exception {
        String token = login(ADMIN_XELA, CONTRASENA_ADMIN);

        mockMvc.perform(conToken(post("/sucursales"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "sucursal", Map.of("nombre", unico("X"), "departamento", "X", "direccion", "X",
                                        "latitud", 14.5, "longitud", -90.5),
                                "administrador", Map.of("nombreCompleto", "X", "correo", unico("x") + "@x.gt")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        // /admin/** se protege en el filtro (AccessDeniedHandler)
        mockMvc.perform(conToken(post("/admin/republicar-eventos"), token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"))
                .andExpect(jsonPath("$.ruta").value("/admin/republicar-eventos"));
    }

    @Test
    void cajero_puedeVerSucursalesPeroNoUsuarios() throws Exception {
        String token = login(CAJERO_XELA, CONTRASENA_CAJERO);

        mockMvc.perform(conToken(get("/sucursales"), token)).andExpect(status().isOk());
        mockMvc.perform(conToken(get("/auth/yo"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("CAJERO"))
                .andExpect(jsonPath("$.nombreSucursal").value("Quetzaltenango"));
        mockMvc.perform(conToken(get("/usuarios"), token)).andExpect(status().isForbidden());
    }

    @Test
    void rutasPublicas() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Servicio de Identidad y Administración"));
    }

    @Test
    void login_devuelveTokenConDatosDelUsuario() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Credenciales("Admin.Xela@Transportes.GT", CONTRASENA_ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiraEn").exists())
                .andExpect(jsonPath("$.usuario.rol").value("ADMIN_SUCURSAL"))
                .andExpect(jsonPath("$.usuario.idSucursal").value(ID_XELA.toString()));
    }

    @Test
    void json_malFormado_responde400() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    void idConFormatoInvalido_responde400() throws Exception {
        String token = login(ADMIN_SISTEMA, CONTRASENA_ADMIN);

        mockMvc.perform(conToken(get("/sucursales/no-es-uuid"), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }
}
