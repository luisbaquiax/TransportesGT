package org.transportsgt.operationalcostsservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CostosIntegrationTest extends IntegracionBase {

    @Test
    void tarifaVigenteDesdeSemilla() throws Exception {
        mockMvc.perform(conToken(get("/costos/depreciacion/tarifa-vigente"), tokenAdminSistema()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.montoPorKm").value(notNullValue()))
                .andExpect(jsonPath("$.vigenteDesde").value("2026-01-01"));
    }

    @Test
    void cambiarTarifaRequiereAdminSistema() throws Exception {
        mockMvc.perform(conToken(post("/costos/depreciacion/tarifas"), tokenAdminSucursal())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json("{\"montoPorKm\":\"2.50\",\"vigenteDesde\":\"2026-12-01\"}")))
                .andExpect(status().isForbidden());
    }

    @Test
    void cambiarTarifaComoAdminSistema() throws Exception {
        MvcResult resultado = mockMvc.perform(conToken(post("/costos/depreciacion/tarifas"),
                                tokenAdminSistema())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json("{\"montoPorKm\":\"2.50\",\"vigenteDesde\":\"2026-09-01\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.montoPorKm").value(notNullValue()))
                .andReturn();
        assertThat(leer(resultado).get("vigenteDesde").asString()).isEqualTo("2026-09-01");
    }

    @Test
    void listarCategoriasSemilla() throws Exception {
        mockMvc.perform(conToken(get("/costos/categorias"), tokenAdminSistema()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].nombre").value(notNullValue()));
    }

    @Test
    void crearYEliminarCategoria() throws Exception {
        MvcResult creado = mockMvc.perform(conToken(post("/costos/categorias"), tokenAdminSistema())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json("{\"nombre\":\"Prueba Cat\",\"descripcion\":\"x\"}")))
                .andExpect(status().isCreated())
                .andReturn();
        String id = leer(creado).get("id").asString();

        mockMvc.perform(conToken(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/costos/categorias/" + id), tokenAdminSistema()))
                .andExpect(status().isNoContent());
    }

    @Test
    void categoriaConGastosNoSeElimina() throws Exception {
        String idGasto = solicitarGastoTaller();
        String idCategoria = jdbc.queryForObject(
                "SELECT id_categoria FROM gasto_taller_linea WHERE id_gasto_taller = ? LIMIT 1",
                String.class, UUID.fromString(idGasto));

        mockMvc.perform(conToken(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/costos/categorias/" + idCategoria), tokenAdminSistema()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CATEGORIA_CON_GASTOS"));
    }

    @Test
    void registrarGastoTallerCalculaTotal() throws Exception {
        sembrarSucursalYBus();
        String idCategoria = jdbc.queryForObject(
                "SELECT id FROM categoria_gasto WHERE lower(nombre) = 'llantas' LIMIT 1", String.class);

        MvcResult resultado = mockMvc.perform(conToken(post("/costos/taller"), tokenAdminSucursal())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"idBus":"%s","fechaServicio":"2026-10-15",
                                 "lineas":[{"idCategoria":"%s","descripcion":"4 llantas",
                                            "tipo":"REPUESTO","monto":"1500.00"},
                                           {"idCategoria":"%s","descripcion":"Mano de obra",
                                            "tipo":"MANO_DE_OBRA","monto":"350.00"}]}"""
                                .formatted(BUS_XELA, idCategoria, idCategoria))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(1850.00))
                .andReturn();

        String id = leer(resultado).get("id").asString();
        assertThat(id).isNotBlank();
        assertThat(contarOutbox(UUID.fromString(id), "GastoTallerRegistrado")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void tallerDeOtraSucursalEs403() throws Exception {
        sembrarSucursalYBus();
        String idCategoria = jdbc.queryForObject(
                "SELECT id FROM categoria_gasto WHERE lower(nombre) = 'llantas' LIMIT 1", String.class);

        mockMvc.perform(conToken(post("/costos/taller"), token(org.transportsgt.eventos.payload.Rol.ADMIN_SUCURSAL, CAPITAL))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"idBus":"%s","fechaServicio":"2026-10-15",
                                 "lineas":[{"idCategoria":"%s","descripcion":"x","tipo":"REPUESTO","monto":"10.00"}]}"""
                                .formatted(BUS_XELA, idCategoria))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("OPERACION_NO_AUTORIZADA"));
    }

    @Test
    void detalleYListadoDeTaller() throws Exception {
        String id = solicitarGastoTaller();

        mockMvc.perform(conToken(get("/costos/taller/" + id), tokenAdminSistema()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1850.00))
                .andExpect(jsonPath("$.lineas.length()").value(2));

        mockMvc.perform(conToken(get("/costos/taller"), tokenAdminSistema()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(notNullValue()));
    }

    @Test
    void viajeSinCostosEs404() throws Exception {
        mockMvc.perform(conToken(get("/costos/viajes/" + UUID.randomUUID()), tokenAdminSistema()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("VIAJE_SIN_COSTOS"));
    }

    @Test
    void sinTokenEs401() throws Exception {
        mockMvc.perform(get("/costos/categorias"))
                .andExpect(status().isUnauthorized());
    }
}
