package org.transportsgt.reportingservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Filtros opcionales (bus, ruta, estado, formato) y errores de la solicitud sobre los datos de {@code ReportesIT}.
 */
@Sql("/sql/reportes-datos.sql")
class ReportesFiltrosIT extends IntegracionBase {

    private static final UUID ANTIGUA = UUID.fromString(ReportesIT.ANTIGUA);
    private static final String OCTUBRE = ReportesIT.OCTUBRE;

    private void ok(String ruta, String token) throws Exception {
        mockMvc.perform(conToken(get(ruta), token)).andExpect(status().isOk());
    }

    @Test
    void reportesDeSucursalConTodosLosFiltros() throws Exception {
        String antigua = tokenSucursal(ANTIGUA);
        ok("/reportes/ingresos-boletos?" + OCTUBRE + "&idRuta=" + ReportesIT.RUTA_COBAN + "&idBus="
                + ReportesIT.BUS_P100 + "&formato=JSON", antigua);
        ok("/reportes/gastos-taller-por-bus?" + OCTUBRE + "&idBus=" + ReportesIT.BUS_P100, antigua);
        ok("/reportes/buses?estado=DISPONIBLE&" + OCTUBRE, antigua);
        ok("/reportes/ingresos-alquileres?" + OCTUBRE, antigua);
        ok("/reportes/depreciacion-buses?" + OCTUBRE, antigua);
        ok("/reportes/choferes?idSucursal=" + ANTIGUA, antigua);
    }

    @Test
    void reportesDelSistemaConSucursalYFormatoJson() throws Exception {
        String sistema = tokenSistema();
        ok("/reportes/gastos?idSucursal=" + ANTIGUA + "&formato=json", sistema);
        ok("/reportes/ganancias?idSucursal=" + ANTIGUA + "&desde=2026-10-01", sistema);
        ok("/reportes/rutas-demandadas?hasta=2026-10-31&idSucursal=" + ANTIGUA, sistema);
        ok("/reportes/gastos-taller-por-categoria?" + OCTUBRE, sistema);
        ok("/reportes/catalogos/rutas?idSucursal=" + ANTIGUA, sistema);
        ok("/reportes/catalogos/buses", tokenSucursal(ANTIGUA));
    }

    @Test
    void busORutaInexistentesResponden404() throws Exception {
        String antigua = tokenSucursal(ANTIGUA);
        mockMvc.perform(conToken(get("/reportes/ingresos-boletos?idBus=" + UUID.randomUUID()), antigua))
                .andExpect(status().isNotFound());
        mockMvc.perform(conToken(get("/reportes/ingresos-boletos?idRuta=" + UUID.randomUUID()), antigua))
                .andExpect(status().isNotFound());
    }

    @Test
    void busORutaDeOtraSucursalResponden403() throws Exception {
        String zacapa = tokenSucursal(UUID.fromString(ReportesIT.ZACAPA));
        mockMvc.perform(conToken(get("/reportes/gastos-taller-por-bus?idBus=" + ReportesIT.BUS_P100), zacapa))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(get("/reportes/ingresos-boletos?idRuta=" + ReportesIT.RUTA_COBAN), zacapa))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void erroresDeLaSolicitud() throws Exception {
        String sistema = tokenSistema();
        mockMvc.perform(conToken(get("/reportes/ingresos?formato=xml"), sistema))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(get("/reportes/ingresos?idSucursal=no-es-uuid"), sistema))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(get("/reportes/ingresos?desde=ayer"), sistema))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(post("/reportes/ingresos"), sistema))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(conToken(get("/reportes/no-existe"), sistema))
                .andExpect(status().isNotFound());
    }

    @Test
    void sinTarifaVigenteYGastosConDatosIncompletos() throws Exception {
        jdbc.update("DELETE FROM rep_tarifa_depreciacion");
        jdbc.update("INSERT INTO rep_gasto (tipo_gasto, id_referencia, id_sucursal, id_bus, id_viaje, fecha_gasto, monto, "
                + "id_categoria, nombre_categoria, tipo_linea, descripcion, distancia_km, monto_por_km) VALUES "
                + "('DEPRECIACION', ?, ?, ?, NULL, DATE '2026-10-12', 50.00, NULL, NULL, NULL, NULL, NULL, NULL)",
                UUID.randomUUID(), ANTIGUA, UUID.fromString(ReportesIT.BUS_P100));
        ok("/reportes/depreciacion-buses", tokenSucursal(ANTIGUA));
        ok("/reportes/gastos?" + OCTUBRE, tokenSistema());
        ok("/reportes/ganancias", tokenSistema());
    }
}
