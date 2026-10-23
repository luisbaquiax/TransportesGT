package org.transportsgt.tripservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ViajeIntegrationTest extends IntegracionBase {

    @Test
    void flujoCompletoCrearViajeSalidaYLlegada() throws Exception {
        String id = crearViajeRegularFuturo();

        mockMvc.perform(get("/viajes/" + id).header("Authorization", "Bearer " + tokenAdminSucursal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PROGRAMADO"))
                .andExpect(jsonPath("$.tipoViaje").value("REGULAR"));

        // Salida
        mockMvc.perform(conToken(post("/viajes/%s/salida".formatted(id)), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"fechaHoraSalidaReal":"%s","kilometrajeInicial":"10000.00"}"""
                                .formatted(OffsetDateTime.now()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_TRANSITO"))
                .andExpect(jsonPath("$.registroSalida.kilometrajeInicial").value(10000.00));

        // Llegada: distancia = 10500 - 10000
        mockMvc.perform(conToken(post("/viajes/%s/llegada".formatted(id)), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"fechaHoraLlegadaReal":"%s","kilometrajeFinal":"10500.00",
                                 "gastoCombustibleTotal":"350.00"}"""
                                .formatted(OffsetDateTime.now()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FINALIZADO"))
                .andExpect(jsonPath("$.registroLlegada.distanciaRecorridaKm").value(500.00))
                .andExpect(jsonPath("$.registroLlegada.salarioBaseChofer").value(140.00));

        // Salida y llegada son inmutables: segundo intento → 409
        mockMvc.perform(conToken(post("/viajes/%s/salida".formatted(id)), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"fechaHoraSalidaReal":"%s","kilometrajeInicial":"1"}"""
                                .formatted(OffsetDateTime.now()))))
                .andExpect(status().isConflict());

        // El viaje finalizado no se puede eliminar
        mockMvc.perform(conToken(delete("/viajes/" + id), tokenAdminSucursal()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VIAJE_NO_ELIMINABLE"));
    }

    @Test
    void traslapeDeBusResponde409() throws Exception {
        OffsetDateTime salida = OffsetDateTime.now().plusDays(40);
        OffsetDateTime llegada = salida.plusHours(4);
        mockMvc.perform(conToken(post("/viajes"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"idRuta":"%s","idBus":"%s","idChofer":"%s",
                                 "fechaHoraSalida":"%s","fechaHoraLlegadaEstimada":"%s"}"""
                                .formatted(RUTA_XELA_CAPITAL, BUS_1, CHOFER_1, salida, llegada))))
                .andExpect(status().isCreated());

        // Mismo bus, horario traslapado
        mockMvc.perform(conToken(post("/viajes"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"idRuta":"%s","idBus":"%s","idChofer":"%s",
                                 "fechaHoraSalida":"%s","fechaHoraLlegadaEstimada":"%s"}"""
                                .formatted(RUTA_XELA_CAPITAL, BUS_1, CHOFER_2,
                                        salida.plusHours(1), llegada.plusHours(1)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_HORARIO"));
    }

    @Test
    void asignarAlquilerPendienteProgramaElViaje() throws Exception {
        UUID idAlquiler = UUID.randomUUID();
        UUID idViaje = UUID.randomUUID();
        jdbc.update("INSERT INTO viaje (id, tipo_viaje, estado, id_alquiler, id_sucursal_origen, "
                        + "nombre_origen, nombre_destino, fecha_hora_salida, "
                        + "fecha_hora_llegada_estimada, numero_pasajeros, version, creado_por) "
                        + "VALUES (?, 'ALQUILER_PRIVADO', 'PENDIENTE_ASIGNACION', ?, ?, "
                        + "'Quetzaltenango', 'San Marcos', ?, ?, 20, 0, ?)",
                idViaje, idAlquiler, XELA,
                OffsetDateTime.now().plusDays(20), OffsetDateTime.now().plusDays(20).plusHours(3),
                UUID.randomUUID());

        mockMvc.perform(conToken(patch("/viajes/%s/asignacion".formatted(idViaje)), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"idBus":"%s","idChofer":"%s"}""".formatted(BUS_2, CHOFER_2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PROGRAMADO"))
                .andExpect(jsonPath("$.bus.placa").value("P456DEF"));

        // Segunda asignación → 409 (ya no está pendiente)
        mockMvc.perform(conToken(patch("/viajes/%s/asignacion".formatted(idViaje)), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"idBus":"%s","idChofer":"%s"}""".formatted(BUS_1, CHOFER_1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
    }

    @Test
    void eliminarViajeProgramadoPropagaViajeEliminado() throws Exception {
        String id = crearViajeRegularFuturo();
        UUID idViaje = UUID.fromString(id);

        mockMvc.perform(conToken(delete("/viajes/" + id), tokenAdminSucursal()))
                .andExpect(status().isNoContent());

        esperar(() -> contarOutbox(idViaje, "ViajeEliminado") >= 1, 10);
    }

    @Test
    void listadoConFiltrosYPermisos() throws Exception {
        mockMvc.perform(get("/viajes").param("estado", "PROGRAMADO")
                        .header("Authorization", "Bearer " + tokenAdminSistema()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").isArray());

        // Cajero de XELA sí puede listar
        mockMvc.perform(get("/viajes")
                        .header("Authorization", "Bearer " + tokenCajero()))
                .andExpect(status().isOk());

        // Chofer ve solo los suyos
        mockMvc.perform(get("/viajes")
                        .header("Authorization", "Bearer " + tokenChofer1()))
                .andExpect(status().isOk());
    }

    @Test
    void crearViajeConRecursosInvalidosResponde409() throws Exception {
        OffsetDateTime salida = OffsetDateTime.now().plusDays(25);
        mockMvc.perform(conToken(post("/viajes"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"idRuta":"%s","idBus":"%s","idChofer":"%s",
                                 "fechaHoraSalida":"%s","fechaHoraLlegadaEstimada":"%s"}"""
                                .formatted(RUTA_XELA_CAPITAL, UUID.randomUUID(), CHOFER_1,
                                        salida, salida.plusHours(4)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("BUS_NO_ENCONTRADO"));
    }

    @Test
    void actualizarCambiaFechasDelViaje() throws Exception {
        String id = crearViajeRegularFuturo();
        OffsetDateTime salida = OffsetDateTime.now().plusDays(31);
        mockMvc.perform(conToken(put("/viajes/" + id), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"fechaHoraSalida":"%s","fechaHoraLlegadaEstimada":"%s"}"""
                                .formatted(salida, salida.plusHours(5)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaHoraSalida").exists());
    }
}
