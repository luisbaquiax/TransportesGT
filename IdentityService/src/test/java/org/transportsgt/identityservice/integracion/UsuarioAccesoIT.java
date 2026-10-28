package org.transportsgt.identityservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quién puede listar, ver, crear y modificar usuarios, y validaciones del alta.
 */
class UsuarioAccesoIT extends IntegracionBase {

    private ResultActions crear(String token, String rol, UUID idSucursal, String correo) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>(Map.of("nombreCompleto", "Usuario Prueba", "correo", correo,
                "rol", rol));
        if (idSucursal != null) {
            cuerpo.put("idSucursal", idSucursal);
        }
        return mockMvc.perform(conToken(post("/usuarios"), token)
                .contentType(MediaType.APPLICATION_JSON).content(json(cuerpo)));
    }

    private static String correo() {
        return unico("acceso") + "@transportes.gt";
    }

    @Test
    void listarConFiltrosSegunRol() throws Exception {
        String sistema = login(ADMIN_SISTEMA, CONTRASENA_ADMIN);
        String xela = login(ADMIN_XELA, CONTRASENA_ADMIN);

        mockMvc.perform(conToken(get("/usuarios").param("rol", "CAJERO").param("idSucursal", ID_XELA.toString())
                        .param("activo", "true").param("texto", "xela"), sistema))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(get("/usuarios").param("texto", "  "), xela)).andExpect(status().isOk());
        mockMvc.perform(conToken(get("/usuarios").param("idSucursal", ID_CAPITAL.toString()), xela))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(get("/usuarios"), login(CAJERO_XELA, CONTRASENA_CAJERO)))
                .andExpect(status().isForbidden());
    }

    @Test
    void verDetalleSegunRol() throws Exception {
        String cajero = login(CAJERO_XELA, CONTRASENA_CAJERO);
        String xela = login(ADMIN_XELA, CONTRASENA_ADMIN);
        UUID idCajero = idUsuarioPorCorreo(CAJERO_XELA);
        UUID idAdminCapital = idUsuarioPorCorreo(ADMIN_CAPITAL);

        mockMvc.perform(conToken(get("/usuarios/" + idCajero), cajero)).andExpect(status().isOk());
        mockMvc.perform(conToken(get("/usuarios/" + idAdminCapital), cajero)).andExpect(status().isForbidden());
        mockMvc.perform(conToken(get("/usuarios/" + idCajero), xela)).andExpect(status().isOk());
        mockMvc.perform(conToken(get("/usuarios/" + idAdminCapital), xela)).andExpect(status().isForbidden());
    }

    @Test
    void adminSucursalNoModificaUsuariosAjenos() throws Exception {
        String xela = login(ADMIN_XELA, CONTRASENA_ADMIN);
        mockMvc.perform(conToken(put("/usuarios/" + idUsuarioPorCorreo(ADMIN_CAPITAL)), xela)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombreCompleto", "Otro", "correo", ADMIN_CAPITAL))))
                .andExpect(status().isForbidden());
    }

    @Test
    void altaNoPermitidaPorRol() throws Exception {
        String sistema = login(ADMIN_SISTEMA, CONTRASENA_ADMIN);
        crear(sistema, "CAJERO", ID_XELA, correo()).andExpect(status().isForbidden());
        crear(sistema, "CLIENTE", null, correo()).andExpect(status().isForbidden());
        crear(login(CAJERO_XELA, CONTRASENA_CAJERO), "CHOFER", null, correo()).andExpect(status().isForbidden());
    }

    @Test
    void altaConSucursalInvalidaOCorreoRepetido() throws Exception {
        String sistema = login(ADMIN_SISTEMA, CONTRASENA_ADMIN);
        crear(sistema, "ADMIN_SUCURSAL", null, correo()).andExpect(status().is4xxClientError());
        crear(sistema, "ADMIN_SUCURSAL", UUID.randomUUID(), correo()).andExpect(status().is4xxClientError());
        crear(sistema, "ADMIN_SISTEMA", ID_XELA, correo()).andExpect(status().is4xxClientError());
        crear(sistema, "ADMIN_SUCURSAL", ID_XELA, ADMIN_XELA.toUpperCase()).andExpect(status().isConflict());
    }

    @Test
    void adminSucursalCreaChoferSinIndicarSucursal() throws Exception {
        crear(login(ADMIN_XELA, CONTRASENA_ADMIN), "CHOFER", null, correo()).andExpect(status().isCreated());
    }

    @Test
    void erroresDeLaSolicitud() throws Exception {
        String sistema = login(ADMIN_SISTEMA, CONTRASENA_ADMIN);
        mockMvc.perform(conToken(get("/usuarios/no-es-uuid"), sistema)).andExpect(status().isBadRequest());
        mockMvc.perform(conToken(get("/usuarios/" + UUID.randomUUID()), sistema)).andExpect(status().isNotFound());
        mockMvc.perform(conToken(post("/usuarios"), sistema)
                        .contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(get("/usuarios").param("rol", "NO_EXISTE"), sistema))
                .andExpect(status().isBadRequest());
    }
}
