package org.transportsgt.reportingservice.integracion;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;
import org.transportsgt.eventos.payload.Rol;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los 12 reportes en JSON sobre datos conocidos ({@code sql/reportes-datos.sql}), con y sin intervalo, y la regla
 * de sucursal del token.
 */
@Sql("/sql/reportes-datos.sql")
class ReportesIT extends IntegracionBase {

    static final String ANTIGUA = "20000000-0000-4000-8000-000000000001";
    static final String ZACAPA = "20000000-0000-4000-8000-000000000002";
    static final String BUS_P100 = "2b000000-0000-4000-8000-000000000001";
    static final String RUTA_COBAN = "2a000000-0000-4000-8000-000000000002";
    static final String OCTUBRE = "desde=2026-10-01&hasta=2026-10-31";

    private JsonNode pedir(String ruta, String token) throws Exception {
        return leer(mockMvc.perform(conToken(get(ruta), token)).andExpect(status().isOk()).andReturn());
    }

    private JsonNode sistema(String ruta) throws Exception {
        return pedir(ruta, tokenSistema());
    }

    private JsonNode sucursalAntigua(String ruta) throws Exception {
        return pedir(ruta, tokenSucursal(UUID.fromString(ANTIGUA)));
    }

    private static List<String> titulos(JsonNode reporte) {
        List<String> titulos = new ArrayList<>();
        reporte.get("grupos").forEach(g -> titulos.add(g.get("titulo").asString()));
        return titulos;
    }

    private static JsonNode filas(JsonNode reporte) {
        return reporte.get("grupos").get(0).get("filas");
    }

    private static List<String> columna(JsonNode filas, String campo) {
        List<String> valores = new ArrayList<>();
        filas.forEach(f -> valores.add(f.get(campo).asString()));
        return valores;
    }

    @Nested
    class ReportesDelSistema {

        @Test
        void ingresos_sinIntervalo_agrupaPorSucursalYExcluyeAnulados() throws Exception {
            JsonNode r = sistema("/reportes/ingresos");
            assertAll(
                    () -> assertEquals(List.of("Antigua", "Zacapa"), titulos(r)),
                    () -> assertEquals("750.00", r.get("grupos").get(0).get("subtotales").get("monto").asString()),
                    () -> assertEquals(3, r.get("grupos").get(0).get("filas").size()),
                    () -> assertEquals("200.00", r.get("grupos").get(1).get("subtotales").get("monto").asString()),
                    () -> assertEquals("950.00", r.get("totales").get("monto").asString()),
                    () -> assertEquals(9, r.get("totales").get("boletos").asInt()),
                    () -> assertTrue(r.get("filtros").get("desde").isNull())
            );
        }

        @Test
        void ingresos_conIntervaloYSucursal_filtraPorFechaDePago() throws Exception {
            JsonNode r = sistema("/reportes/ingresos?" + OCTUBRE + "&idSucursal=" + ANTIGUA);
            assertAll(
                    () -> assertEquals(List.of("Antigua"), titulos(r)),
                    () -> assertEquals("750.00", r.get("totales").get("monto").asString()),
                    () -> assertEquals("2026-10-01", r.get("filtros").get("desde").asString()),
                    () -> assertEquals("Antigua", r.get("filtros").get("otros").get("sucursal").asString())
            );
        }

        @Test
        void gastos_sinIntervalo_desglosaPorTipoConTallerYRepuestoSeparados() throws Exception {
            JsonNode r = sistema("/reportes/gastos");
            JsonNode antigua = r.get("grupos").get(0).get("subtotales");
            assertAll(
                    () -> assertEquals(List.of("Antigua", "Zacapa"), titulos(r)),
                    () -> assertEquals("2150.00", antigua.get("monto").asString()),
                    () -> assertEquals("1400.00", antigua.get("REPUESTO").asString()),
                    () -> assertEquals("100.00", antigua.get("TALLER").asString()),
                    () -> assertEquals("150.00", antigua.get("SALARIO_CHOFER").asString()),
                    () -> assertEquals("2500.00", r.get("totales").get("monto").asString())
            );
        }

        @Test
        void gastos_conIntervalo_soloOctubre() throws Exception {
            JsonNode r = sistema("/reportes/gastos?" + OCTUBRE);
            assertAll(
                    () -> assertEquals(List.of("Antigua"), titulos(r)),
                    () -> assertEquals("2150.00", r.get("totales").get("monto").asString())
            );
        }

        @Test
        void ganancias_incluyeSucursalesEnCeroEnOrdenAlfabetico() throws Exception {
            JsonNode todo = sistema("/reportes/ganancias");
            JsonNode octubre = sistema("/reportes/ganancias?" + OCTUBRE);
            assertAll(
                    () -> assertEquals(List.of("Antigua", "Cobán", "Zacapa"), columna(filas(todo), "sucursal")),
                    () -> assertEquals("-1400.00", filas(todo).get(0).get("ganancia").asString()),
                    () -> assertEquals("0.00", filas(todo).get(1).get("ingresos").asString()),
                    () -> assertEquals("-1550.00", todo.get("totales").get("ganancia").asString()),
                    () -> assertEquals("0.00", filas(octubre).get(2).get("gastos").asString()),
                    () -> assertEquals("750.00", octubre.get("totales").get("ingresos").asString())
            );
        }

        @Test
        void rutasDemandadas_ordenaPorBoletosEIncluyeEliminadasConVentas() throws Exception {
            JsonNode todo = sistema("/reportes/rutas-demandadas");
            JsonNode octubre = sistema("/reportes/rutas-demandadas?" + OCTUBRE);
            assertAll(
                    () -> assertEquals(List.of("5", "4", "0"), columna(filas(todo), "boletosVendidos")),
                    () -> assertTrue(filas(todo).get(1).get("eliminada").asBoolean()),
                    () -> assertEquals("Zacapa", filas(todo).get(0).get("destino").asString()),
                    () -> assertEquals(2, filas(todo).get(0).get("viajesConVentas").asInt()),
                    () -> assertEquals(9, todo.get("totales").get("boletosVendidos").asInt()),
                    () -> assertEquals(2, filas(octubre).size()),
                    () -> assertEquals(5, octubre.get("totales").get("boletosVendidos").asInt())
            );
        }

        @Test
        void tallerPorCategoria_deMayorAMenorYConFiltroDeSucursal() throws Exception {
            JsonNode todo = sistema("/reportes/gastos-taller-por-categoria");
            JsonNode antigua = sistema("/reportes/gastos-taller-por-categoria?idSucursal=" + ANTIGUA);
            JsonNode porBus = sistema("/reportes/gastos-taller-por-categoria?" + OCTUBRE + "&idBus=" + BUS_P100);
            assertAll(
                    () -> assertEquals(List.of("Motor", "Frenos", "Llantas"), titulos(todo)),
                    () -> assertEquals("1750.00", todo.get("totales").get("monto").asString()),
                    () -> assertEquals(List.of("Motor", "Frenos"), titulos(antigua)),
                    () -> assertEquals(List.of("Frenos"), titulos(porBus)),
                    () -> assertEquals("P100AAA", porBus.get("filtros").get("otros").get("bus").asString())
            );
        }

        @Test
        void mapaRutas_devuelveRutasVigentesConCoordenadas() throws Exception {
            JsonNode r = sistema("/reportes/mapa-rutas?idSucursal=" + ANTIGUA);
            assertAll(
                    () -> assertEquals(List.of("Cobán", "Zacapa"), columna(filas(r), "destino")),
                    () -> assertEquals(14.5574, filas(r).get(0).get("latitudOrigen").asDouble(), 0.0001),
                    () -> assertEquals(-90.3708, filas(r).get(0).get("longitudDestino").asDouble(), 0.0001),
                    () -> assertEquals(2, r.get("totales").get("rutas").asInt())
            );
        }

        @Test
        void mapaRutas_sinSucursal_responde400() throws Exception {
            mockMvc.perform(conToken(get("/reportes/mapa-rutas"), tokenSistema()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        }
    }

    @Nested
    class ReportesDeSucursal {

        @Test
        void buses_choferActualViajesYFiltroPorEstado() throws Exception {
            JsonNode todos = sucursalAntigua("/reportes/buses");
            JsonNode enTaller = sucursalAntigua("/reportes/buses?estado=EN_TALLER");
            assertAll(
                    () -> assertEquals(List.of("P100AAA", "P101AAA"), columna(filas(todos), "placa")),
                    () -> assertEquals("Beatriz López", filas(todos).get(0).get("chofer").asString()),
                    () -> assertEquals(2, filas(todos).get(0).get("viajesRealizados").asInt()),
                    () -> assertTrue(filas(todos).get(1).get("chofer").isNull()),
                    () -> assertEquals(List.of("P101AAA"), columna(filas(enTaller), "placa"))
            );
        }

        @Test
        void buses_conIntervalo_cuentaViajesConLlegadaEnElIntervalo() throws Exception {
            JsonNode r = sucursalAntigua("/reportes/buses?desde=2026-10-11&hasta=2026-10-31");
            assertEquals(1, filas(r).get(0).get("viajesRealizados").asInt());
        }

        @Test
        void choferes_marcaVencidasYPorVencer() throws Exception {
            JsonNode r = sucursalAntigua("/reportes/choferes");
            assertAll(
                    () -> assertEquals(List.of("Beatriz López", "Carlos Ñuñez"), columna(filas(r), "nombre")),
                    () -> assertEquals(List.of("VENCIDA", "POR_VENCER"), columna(filas(r), "estadoLicencia")),
                    () -> assertEquals(List.of("0", "1"), columna(filas(r), "viajesRealizados"))
            );
        }

        @Test
        void ingresosBoletos_porViajeSinAnuladosYConFiltros() throws Exception {
            JsonNode todo = sucursalAntigua("/reportes/ingresos-boletos");
            JsonNode porBus = sucursalAntigua("/reportes/ingresos-boletos?idBus=" + BUS_P100 + "&desde=2026-10-10");
            JsonNode rutaAnulada = sucursalAntigua("/reportes/ingresos-boletos?idRuta=" + RUTA_COBAN);
            assertAll(
                    () -> assertEquals(List.of("3", "2"), columna(filas(todo), "boletosVendidos")),
                    () -> assertEquals("Antigua → Zacapa", filas(todo).get(0).get("ruta").asString()),
                    () -> assertEquals("2026-10-10T06:00:00-06:00", filas(todo).get(0).get("fechaHoraSalida").asString()),
                    () -> assertEquals("250.00", todo.get("totales").get("ingreso").asString()),
                    () -> assertEquals(1, filas(porBus).size()),
                    () -> assertEquals(0, filas(rutaAnulada).size()),
                    () -> assertEquals("Antigua → Cobán", rutaAnulada.get("filtros").get("otros").get("ruta").asString())
            );
        }

        @Test
        void ingresosAlquileres_conBusDelViajeDelAlquiler() throws Exception {
            JsonNode todo = sucursalAntigua("/reportes/ingresos-alquileres");
            JsonNode septiembre = sucursalAntigua("/reportes/ingresos-alquileres?desde=2026-09-01&hasta=2026-09-30");
            assertAll(
                    () -> assertEquals("P100AAA", filas(todo).get(0).get("placa").asString()),
                    () -> assertEquals("Empresa Ñandú", filas(todo).get(0).get("cliente").asString()),
                    () -> assertEquals("500.00", todo.get("totales").get("precioTotal").asString()),
                    () -> assertEquals(0, filas(septiembre).size())
            );
        }

        @Test
        void depreciacion_usaLaTarifaVigenteHoy() throws Exception {
            JsonNode r = sucursalAntigua("/reportes/depreciacion-buses");
            assertAll(
                    () -> assertEquals("2.00", filas(r).get(0).get("montoPorKmActual").asString()),
                    () -> assertEquals("200.00", filas(r).get(0).get("depreciacionAcumulada").asString()),
                    () -> assertEquals("2.00", filas(r).get(0).get("promedioPorKm").asString()),
                    () -> assertEquals("0.00", filas(r).get(1).get("depreciacionAcumulada").asString()),
                    () -> assertEquals("200.00", r.get("totales").get("depreciacionAcumulada").asString())
            );
        }

        @Test
        void tallerPorBus_busesDeMayorAMenorConSubtotalPorCategoria() throws Exception {
            JsonNode r = sucursalAntigua("/reportes/gastos-taller-por-bus");
            JsonNode p100 = r.get("grupos").get(1);
            assertAll(
                    () -> assertEquals(List.of("P101AAA", "P100AAA"), titulos(r)),
                    () -> assertEquals("Frenos", p100.get("subgrupos").get(0).get("titulo").asString()),
                    () -> assertEquals("500.00", p100.get("subtotales").get("monto").asString()),
                    () -> assertEquals("1500.00", r.get("totales").get("monto").asString())
            );
        }

        @Test
        void adminSistema_abreReporteDeSucursalConIdSucursal() throws Exception {
            JsonNode r = sistema("/reportes/choferes?idSucursal=" + ZACAPA);
            assertEquals(List.of("Diego Ruiz"), columna(filas(r), "nombre"));
        }

        @Test
        void catalogos_listanSucursalesRutasYBuses() throws Exception {
            JsonNode sucursales = sistema("/reportes/catalogos/sucursales");
            JsonNode rutas = sucursalAntigua("/reportes/catalogos/rutas");
            JsonNode buses = sucursalAntigua("/reportes/catalogos/buses");
            assertAll(
                    () -> assertEquals("Antigua", sucursales.get(0).get("nombre").asString()),
                    () -> assertEquals(2, rutas.size()),
                    () -> assertEquals("P100AAA (Volvo B12R)", buses.get(0).get("nombre").asString())
            );
        }
    }

    @Nested
    class Seguridad {

        @Test
        void adminSucursal_pidiendoOtraSucursal_responde403() throws Exception {
            mockMvc.perform(conToken(get("/reportes/buses?idSucursal=" + ZACAPA), tokenSucursal(UUID.fromString(ANTIGUA))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        }

        @Test
        void adminSucursal_sinPedirSucursal_soloVeLaSuya() throws Exception {
            JsonNode r = sucursalAntigua("/reportes/choferes");
            assertAll(
                    () -> assertEquals("Antigua", r.get("filtros").get("otros").get("sucursal").asString()),
                    () -> assertFalse(columna(filas(r), "nombre").contains("Diego Ruiz"))
            );
        }

        @Test
        void adminSucursal_enReporteDelSistema_responde403() throws Exception {
            mockMvc.perform(conToken(get("/reportes/ingresos"), tokenSucursal(UUID.fromString(ANTIGUA))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(conToken(get("/reportes/catalogos/sucursales"), tokenSucursal(UUID.fromString(ANTIGUA))))
                    .andExpect(status().isForbidden());
        }

        @Test
        void cajero_enReporteDeSucursal_responde403YSinToken401() throws Exception {
            mockMvc.perform(conToken(get("/reportes/buses"), token(Rol.CAJERO, UUID.fromString(ANTIGUA))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/reportes/buses")).andExpect(status().isUnauthorized());
        }

        @Test
        void desdePosteriorAHasta_responde400() throws Exception {
            mockMvc.perform(conToken(get("/reportes/ingresos?desde=2026-10-31&hasta=2026-10-01"), tokenSistema()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        }

        @Test
        void adminSistemaSinSucursalEnReporteDeSucursal_responde400YFormatoPdf_400() throws Exception {
            mockMvc.perform(conToken(get("/reportes/buses"), tokenSistema())).andExpect(status().isBadRequest());
            // Hasta que exista JasperReports
            mockMvc.perform(conToken(get("/reportes/ingresos?formato=pdf"), tokenSistema()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void sucursalInexistente_responde404() throws Exception {
            mockMvc.perform(conToken(get("/reportes/ingresos?idSucursal=" + UUID.randomUUID()), tokenSistema()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        }
    }
}
