package org.transportsgt.operationalcostsservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Casos de error de punta a punta: reglas de categorías, visibilidad y respuestas del manejador global.
 */
class ErroresCostosIT extends IntegracionBase {

    private String categoriaSemilla() {
        return jdbc.queryForObject("SELECT nombre FROM categoria_gasto ORDER BY nombre LIMIT 1", String.class);
    }

    @Test
    void crearCategoriaConNombreRepetidoDevuelve409() throws Exception {
        mockMvc.perform(conToken(post("/costos/categorias"), tokenAdminSistema())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"nombre\":\"" + categoriaSemilla().toUpperCase() + "\"}")))
                .andExpect(status().isConflict());
    }

    @Test
    void actualizarCategoriaInexistenteDevuelve404() throws Exception {
        mockMvc.perform(conToken(put("/costos/categorias/" + UUID.randomUUID()), tokenAdminSistema())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"nombre\":\"No existe\"}")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CATEGORIA_NO_ENCONTRADA"));
    }

    @Test
    void actualizarCategoriaConNombreDeOtraDevuelve409() throws Exception {
        String id = jdbc.queryForObject("SELECT id::text FROM categoria_gasto ORDER BY nombre DESC LIMIT 1",
                String.class);
        mockMvc.perform(conToken(put("/costos/categorias/" + id), tokenAdminSistema())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"nombre\":\"" + categoriaSemilla() + "\"}")))
                .andExpect(status().isConflict());
    }

    @Test
    void desactivarYEliminarCategoriaInexistenteDevuelve404() throws Exception {
        mockMvc.perform(conToken(patch("/costos/categorias/" + UUID.randomUUID() + "/activa"), tokenAdminSistema())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"activa\":false}")))
                .andExpect(status().isNotFound());
        mockMvc.perform(conToken(delete("/costos/categorias/" + UUID.randomUUID()), tokenAdminSistema()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listarSoloCategoriasActivas() throws Exception {
        mockMvc.perform(conToken(get("/costos/categorias").param("activa", "true"), tokenAdminSistema()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].activa").value(true));
    }

    @Test
    void listarYDetalleDeTallerComoAdminSistema() throws Exception {
        solicitarGastoTaller();
        mockMvc.perform(conToken(get("/costos/taller"), tokenAdminSistema()))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(get("/costos/taller/" + UUID.randomUUID()), tokenAdminSistema()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("GASTO_NO_ENCONTRADO"));
    }

    @Test
    void resumenDeViajeSinCostosParaAdminSucursalDevuelve403() throws Exception {
        mockMvc.perform(conToken(get("/costos/viajes/" + UUID.randomUUID()), tokenAdminSucursal()))
                .andExpect(status().isForbidden());
    }

    @Test
    void erroresDeLaSolicitud() throws Exception {
        mockMvc.perform(conToken(post("/costos/categorias"), tokenAdminSistema())
                        .contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(post("/costos/categorias"), tokenAdminSistema())
                        .contentType(MediaType.APPLICATION_JSON).content(json("{\"nombre\":\"\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
        mockMvc.perform(conToken(get("/costos/viajes/no-es-uuid"), tokenAdminSistema()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(delete("/costos/depreciacion/tarifa-vigente"), tokenAdminSistema()))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(conToken(get("/costos/no-existe"), tokenAdminSistema()))
                .andExpect(status().isNotFound());
    }
}
