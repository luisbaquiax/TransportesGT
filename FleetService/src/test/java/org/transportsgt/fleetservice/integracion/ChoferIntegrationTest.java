package org.transportsgt.fleetservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.transportsgt.eventos.payload.Rol;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integración HTTP + PostgreSQL: CRUD de choferes, licencias únicas, enlaces de
 * usuario y eventos outbox.
 */
class ChoferIntegrationTest extends IntegracionBase {

    @Test
    void listarTraeSemilla() throws Exception {
        mockMvc.perform(conToken(get("/choferes"), tokenAdminSistema()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos", greaterThanOrEqualTo(9)))
                .andExpect(jsonPath("$.contenido[?(@.numeroLicencia=='" + LICENCIA_SEMILLA + "')]").exists());
    }

    @Test
    void listarSoloSucursalDelToken() throws Exception {
        mockMvc.perform(conToken(get("/choferes"), tokenAdminSucursal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(greaterThanOrEqualTo(3))));
        mockMvc.perform(conToken(get("/choferes"), token(Rol.ADMIN_SUCURSAL, HUEHUE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].idSucursal").value(HUEHUE.toString()));
    }

    @Test
    void crearChoferRegistraOutbox() throws Exception {
        String licencia = unico("LICT");
        MvcResult resultado = mockMvc.perform(conToken(post("/choferes"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"nombreCompleto":"Ana de Prueba","correo":"%s@transportes.gt",
                                 "telefono":"5555-1111","numeroLicencia":"%s","tipoLicencia":"C",
                                 "fechaVencimientoLicencia":"2029-06-01","salarioBaseViaje":"175.50"}"""
                                .formatted(licencia.toLowerCase(), licencia))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroLicencia").value(licencia))
                .andExpect(jsonPath("$.activo").value(true))
                .andReturn();

        UUID id = UUID.fromString(leer(resultado).get("id").asString());
        assertThat(contarOutbox(id, "ChoferRegistrado")).isEqualTo(1);
    }

    @Test
    void licenciaDuplicadaDevuelve409() throws Exception {
        String licencia = unico("LDUP");
        crearChofer(licencia);
        mockMvc.perform(conToken(post("/choferes"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"nombreCompleto":"Otro","correo":"otro@transportes.gt",
                                 "telefono":"5555-2222","numeroLicencia":"%s","tipoLicencia":"B",
                                 "fechaVencimientoLicencia":"2029-01-01","salarioBaseViaje":"100.00"}"""
                                .formatted(licencia))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("LICENCIA_DUPLICADA"));
    }

    @Test
    void idUsuarioDuplicadoDevuelve409() throws Exception {
        UUID usuario = UUID.randomUUID();
        String licA = unico("LUA");
        String licB = unico("LUB");
        mockMvc.perform(conToken(post("/choferes"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"nombreCompleto":"Uno","correo":"uno@transportes.gt","telefono":"5555-0001",
                                 "numeroLicencia":"%s","tipoLicencia":"B","fechaVencimientoLicencia":"2029-01-01",
                                 "salarioBaseViaje":"100.00","idUsuario":"%s"}""".formatted(licA, usuario))))
                .andExpect(status().isCreated());
        mockMvc.perform(conToken(post("/choferes"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"nombreCompleto":"Dos","correo":"dos@transportes.gt","telefono":"5555-0002",
                                 "numeroLicencia":"%s","tipoLicencia":"B","fechaVencimientoLicencia":"2029-01-01",
                                 "salarioBaseViaje":"100.00","idUsuario":"%s"}""".formatted(licB, usuario))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("USUARIO_YA_ENLAZADO"));
    }

    @Test
    void obtenerActualizarYCambiarActivo() throws Exception {
        String licencia = unico("LGET");
        String id = crearChofer(licencia);
        String correo = jdbc.queryForObject("SELECT correo FROM chofer WHERE id = ?", String.class,
                UUID.fromString(id));

        mockMvc.perform(conToken(get("/choferes/" + id), tokenAdminSistema()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroLicencia").value(licencia));

        mockMvc.perform(conToken(put("/choferes/" + id), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"nombreCompleto":"Chofer Actualizado","correo":"%s",
                                 "telefono":"5555-9999","numeroLicencia":"%s","tipoLicencia":"C",
                                 "fechaVencimientoLicencia":"2030-12-31","salarioBaseViaje":"200.00",
                                 "version":0}""".formatted(correo, licencia))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreCompleto").value("Chofer Actualizado"))
                .andExpect(jsonPath("$.version").value(1));

        // Versión vieja → conflicto
        mockMvc.perform(conToken(put("/choferes/" + id), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"nombreCompleto":"Viejo","correo":"%s",
                                 "telefono":"5555-9999","numeroLicencia":"%s","tipoLicencia":"C",
                                 "fechaVencimientoLicencia":"2030-12-31","salarioBaseViaje":"200.00",
                                 "version":0}""".formatted(correo, licencia))))
                .andExpect(status().isConflict());

        mockMvc.perform(conToken(patch("/choferes/" + id + "/activo"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"activo\":false,\"version\":1}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        assertThat(jdbc.queryForObject("SELECT activo FROM chofer WHERE id = ?", Boolean.class,
                UUID.fromString(id))).isFalse();
    }

    @Test
    void choferDeOtraSucursalDevuelve403() throws Exception {
        // Choferes sembrados: 0001-0003 XELA, 0004-0006 CAPITAL, 0007-0009 HUEHUE
        String idCapital = jdbc.queryForObject(
                "SELECT id FROM chofer WHERE id = 'c0000000-0000-4000-8000-000000000004'", String.class);
        mockMvc.perform(conToken(get("/choferes/" + idCapital), tokenAdminSucursal()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("OPERACION_NO_AUTORIZADA"));
        // Con su sucursal → 200
        mockMvc.perform(conToken(get("/choferes/" + idCapital), token(Rol.ADMIN_SUCURSAL, CAPITAL)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteSqlDisparaTriggerDeAuditoria() throws Exception {
        String id = crearChofer(unico("LDEL"));
        String sql = "DELETE FROM chofer WHERE id = '" + id + "'";
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> jdbc.execute(sql));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chofer WHERE id = ?", Integer.class,
                UUID.fromString(id)))
                .isEqualTo(1);
    }
}
