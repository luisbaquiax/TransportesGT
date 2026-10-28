package org.transportsgt.tripservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RutaIT extends IntegracionBase {

    @Test
    void crudDeRutasConPermisos() throws Exception {
        UUID destino = UUID.randomUUID();
        jdbc.update("INSERT INTO sucursal_ref (id, nombre, activa, version) VALUES (?, 'Retalhuleu', true, 0)",
                destino);

        // 403: admin de otra sucursal no crea rutas para XELA
        mockMvc.perform(conToken(post("/rutas"),
                        token(org.transportsgt.eventos.payload.identidad.Rol.ADMIN_SUCURSAL, CAPITAL, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"idSucursalOrigen":"%s","idSucursalDestino":"%s",
                                 "distanciaKm":"90.00","precioBoleto":"40.00"}"""
                                .formatted(XELA, destino))))
                .andExpect(status().isForbidden());

        // Creación por el admin de la sucursal de origen
        var creado = mockMvc.perform(conToken(post("/rutas"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"idSucursalOrigen":"%s","idSucursalDestino":"%s",
                                 "distanciaKm":"90.00","precioBoleto":"40.00"}"""
                                .formatted(XELA, destino))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idSucursalOrigen").value(XELA.toString()))
                .andReturn();
        String id = leer(creado).get("id").asString();

        // Duplicada → 409
        mockMvc.perform(conToken(post("/rutas"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"idSucursalOrigen":"%s","idSucursalDestino":"%s",
                                 "distanciaKm":"90.00","precioBoleto":"40.00"}"""
                                .formatted(XELA, destino))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RUTA_DUPLICADA"));

        // Actualizar
        mockMvc.perform(conToken(put("/rutas/" + id), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"idSucursalOrigen":"%s","idSucursalDestino":"%s",
                                 "distanciaKm":"95.00","precioBoleto":"45.00"}"""
                                .formatted(XELA, destino))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precioBoleto").value(45.00));

        // Detalle
        mockMvc.perform(get("/rutas/" + id)
                        .header("Authorization", "Bearer " + tokenAdminSistema()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distanciaKm").value(95.00));

        // Eliminar sin viajes
        mockMvc.perform(conToken(delete("/rutas/" + id), tokenAdminSucursal()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/rutas/" + id)
                        .header("Authorization", "Bearer " + tokenAdminSistema()))
                .andExpect(status().isNotFound());
    }

    @Test
    void eliminarRutaConViajesResponde409() throws Exception {
        mockMvc.perform(conToken(delete("/rutas/" + RUTA_XELA_CAPITAL), tokenAdminSucursal()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RUTA_CON_VIAJES"));
    }

    @Test
    void origenYDestinoDistintosSonObligatorios() throws Exception {
        mockMvc.perform(conToken(post("/rutas"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"idSucursalOrigen":"%s","idSucursalDestino":"%s",
                                 "distanciaKm":"90.00","precioBoleto":"40.00"}"""
                                .formatted(XELA, XELA))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RUTA_INVALIDA"));
    }

    @Test
    void listarRutasSinTokenEs401() throws Exception {
        mockMvc.perform(get("/rutas")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/rutas").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
