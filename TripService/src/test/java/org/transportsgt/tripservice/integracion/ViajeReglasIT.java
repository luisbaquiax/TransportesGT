package org.transportsgt.tripservice.integracion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.transportsgt.eventos.payload.identidad.Rol;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reglas de recursos al programar (bus y chofer) y visibilidad de un viaje según el rol.
 */
class ViajeReglasIT extends IntegracionBase {

    private static final UUID BUS_CAPITAL = UUID.fromString("20000000-0000-4000-8000-000000000003");
    private static final UUID BUS_INACTIVO = UUID.fromString("20000000-0000-4000-8000-000000000091");
    private static final UUID BUS_TALLER = UUID.fromString("20000000-0000-4000-8000-000000000092");
    private static final UUID CHOFER_CAPITAL = UUID.fromString("30000000-0000-4000-8000-000000000003");
    private static final UUID CHOFER_INACTIVO = UUID.fromString("30000000-0000-4000-8000-000000000091");
    private static final UUID CHOFER_VENCIDO = UUID.fromString("30000000-0000-4000-8000-000000000092");
    private static final UUID RUTA_CAPITAL_XELA = UUID.fromString("40000000-0000-4000-8000-000000000003");
    private static final OffsetDateTime SALIDA = OffsetDateTime.now().plusDays(60).withNano(0);

    @BeforeEach
    void recursosNoDisponibles() {
        jdbc.update("""
                INSERT INTO bus_ref (id, id_sucursal, placa, capacidad_pasajeros, estado_operativo, activo, version) VALUES
                 (?, ?, 'P991AAA', 40, 'DISPONIBLE', FALSE, 1),
                 (?, ?, 'P992AAA', 40, 'EN_TALLER', TRUE, 1)
                ON CONFLICT (id) DO NOTHING""", BUS_INACTIVO, XELA, BUS_TALLER, XELA);
        jdbc.update("""
                INSERT INTO chofer_ref (id, id_usuario, id_sucursal, nombre_completo, salario_base_viaje,
                                        fecha_vencimiento_licencia, activo, version) VALUES
                 (?, NULL, ?, 'Chofer Inactivo', 100.00, '2030-01-01', FALSE, 1),
                 (?, NULL, ?, 'Chofer Vencido', 100.00, CURRENT_DATE + 1, TRUE, 1)
                ON CONFLICT (id) DO NOTHING""", CHOFER_INACTIVO, XELA, CHOFER_VENCIDO, XELA);
    }

    private ResultActions programar(UUID idRuta, UUID idBus, UUID idChofer, OffsetDateTime llegada) throws Exception {
        return mockMvc.perform(conToken(post("/viajes"), tokenAdminSucursal())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("""
                        {"idRuta":"%s","idBus":"%s","idChofer":"%s",
                         "fechaHoraSalida":"%s","fechaHoraLlegadaEstimada":"%s"}"""
                        .formatted(idRuta, idBus, idChofer, SALIDA, llegada))));
    }

    private ResultActions programar(UUID idBus, UUID idChofer) throws Exception {
        return programar(RUTA_XELA_CAPITAL, idBus, idChofer, SALIDA.plusHours(4));
    }

    @Test
    void busesNoValidos() throws Exception {
        programar(UUID.randomUUID(), CHOFER_2).andExpect(jsonPath("$.codigo").value("BUS_NO_ENCONTRADO"));
        programar(BUS_INACTIVO, CHOFER_2).andExpect(jsonPath("$.codigo").value("BUS_INACTIVO"));
        programar(BUS_CAPITAL, CHOFER_2).andExpect(jsonPath("$.codigo").value("BUS_OTRA_SUCURSAL"));
        programar(BUS_TALLER, CHOFER_2).andExpect(jsonPath("$.codigo").value("BUS_NO_DISPONIBLE"));
    }

    @Test
    void choferesNoValidos() throws Exception {
        programar(BUS_2, UUID.randomUUID()).andExpect(jsonPath("$.codigo").value("CHOFER_NO_ENCONTRADO"));
        programar(BUS_2, CHOFER_INACTIVO).andExpect(jsonPath("$.codigo").value("CHOFER_INACTIVO"));
        programar(BUS_2, CHOFER_CAPITAL).andExpect(jsonPath("$.codigo").value("CHOFER_OTRA_SUCURSAL"));
        programar(BUS_2, CHOFER_VENCIDO).andExpect(jsonPath("$.codigo").value("LICENCIA_VENCIDA"));
    }

    @Test
    void horarioYRutaNoValidos() throws Exception {
        programar(RUTA_XELA_CAPITAL, BUS_2, CHOFER_2, SALIDA.minusHours(1)).andExpect(status().isBadRequest());
        programar(RUTA_CAPITAL_XELA, BUS_2, CHOFER_2, SALIDA.plusHours(4)).andExpect(status().isForbidden());
        programar(UUID.randomUUID(), BUS_2, CHOFER_2, SALIDA.plusHours(4)).andExpect(status().is4xxClientError());
    }

    @Test
    void visibilidadYPermisosSobreUnViaje() throws Exception {
        // Fecha propia: otros IT programan BUS_2/CHOFER_2 a 30 días en el mismo contenedor
        OffsetDateTime salida = OffsetDateTime.now().plusDays(200 + (long) (Math.random() * 500)).withNano(0);
        String id = leer(mockMvc.perform(conToken(post("/viajes"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"idRuta":"%s","idBus":"%s","idChofer":"%s",
                                 "fechaHoraSalida":"%s","fechaHoraLlegadaEstimada":"%s"}"""
                                .formatted(RUTA_XELA_CAPITAL, BUS_2, CHOFER_2, salida, salida.plusHours(4)))))
                .andExpect(status().isCreated()).andReturn()).get("id").asString();
        String choferAsignado = token(Rol.CHOFER, XELA, UUID.fromString("e0000000-0000-4000-8000-000000000002"));

        mockMvc.perform(conToken(get("/viajes/" + id), tokenCajero())).andExpect(status().isOk());
        mockMvc.perform(conToken(get("/viajes/" + id), choferAsignado)).andExpect(status().isOk());
        mockMvc.perform(conToken(get("/viajes/" + id), tokenChofer1())).andExpect(status().isForbidden());
        mockMvc.perform(conToken(get("/viajes/" + id), token(Rol.ADMIN_SUCURSAL, CAPITAL, null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(get("/viajes"), choferAsignado)).andExpect(status().isOk());

        mockMvc.perform(conToken(delete("/viajes/" + id), token(Rol.ADMIN_SUCURSAL, CAPITAL, null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(post("/viajes/" + id + "/salida"), tokenChofer1())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"fechaHoraSalidaReal\":\"%s\",\"kilometrajeInicial\":100}"
                                .formatted(SALIDA))))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(post("/viajes/" + id + "/salida"), token(Rol.ADMIN_SUCURSAL, CAPITAL, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"fechaHoraSalidaReal\":\"%s\",\"kilometrajeInicial\":100}"
                                .formatted(SALIDA))))
                .andExpect(status().isForbidden());
    }

    @Test
    void actualizarYAsignarUnViajeRegular() throws Exception {
        OffsetDateTime salida = OffsetDateTime.now().plusDays(800 + (long) (Math.random() * 500)).withNano(0);
        String id = leer(mockMvc.perform(conToken(post("/viajes"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"idRuta":"%s","idBus":"%s","idChofer":"%s",
                                 "fechaHoraSalida":"%s","fechaHoraLlegadaEstimada":"%s"}"""
                                .formatted(RUTA_XELA_CAPITAL, BUS_2, CHOFER_2, salida, salida.plusHours(4)))))
                .andExpect(status().isCreated()).andReturn()).get("id").asString();

        // Solo la llegada: bus, chofer y salida se conservan
        mockMvc.perform(conToken(put("/viajes/" + id), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"fechaHoraLlegadaEstimada\":\"%s\"}".formatted(salida.plusHours(5)))))
                .andExpect(status().isOk());
        // Cambiar el bus de un regular consulta Boletos, que no existe en las pruebas: falla cerrado
        mockMvc.perform(conToken(put("/viajes/" + id), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"idBus\":\"%s\"}".formatted(BUS_1))))
                .andExpect(status().isServiceUnavailable());
        // La asignación es solo para alquileres pendientes
        mockMvc.perform(conToken(patch("/viajes/" + id + "/asignacion"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"idBus\":\"%s\",\"idChofer\":\"%s\"}".formatted(BUS_2, CHOFER_2))))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(conToken(put("/viajes/" + id), tokenCajero())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void erroresDeLaSolicitud() throws Exception {
        String sistema = tokenAdminSistema();
        mockMvc.perform(conToken(get("/viajes/no-es-uuid"), sistema)).andExpect(status().isBadRequest());
        mockMvc.perform(conToken(get("/viajes/" + UUID.randomUUID()), sistema)).andExpect(status().isNotFound());
        mockMvc.perform(conToken(post("/viajes"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(post("/viajes"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(put("/viajes"), sistema)).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(conToken(get("/viajes/resumen"), sistema)).andExpect(status().is4xxClientError());
        mockMvc.perform(conToken(get("/no-existe"), sistema)).andExpect(status().isNotFound());
    }
}
