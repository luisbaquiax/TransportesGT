package org.transportsgt.ticketservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.viajes.DatosViaje;
import org.transportsgt.eventos.payload.viajes.DatosViajeEliminado;
import org.transportsgt.eventos.payload.viajes.EstadoViaje;
import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.eventos.payload.viajes.TipoViaje;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consumidor de {@code boletos.viajes.q}: proyección de viajes y anulación por ViajeEliminado.
 */
class ViajeEventosIT extends IntegracionBase {

    private int viajes(UUID idViaje) {
        return jdbc.queryForObject("SELECT count(*) FROM viaje_ref WHERE id = ?", Integer.class, idViaje);
    }

    private Map<String, Object> viaje(UUID idViaje) {
        return jdbc.queryForMap("SELECT estado, version, eliminado, capacidad_bus FROM viaje_ref WHERE id = ?", idViaje);
    }

    @Test
    void viajeProgramado_cuandoLlegaDuplicado_proyectaUnSoloViaje() {
        // Arrange
        UUID idViaje = UUID.randomUUID();
        DatosViaje datos = datosViaje(idViaje, EstadoViaje.PROGRAMADO, manana());
        SobreEvento<DatosViaje> evento = sobre(TipoEvento.VIAJE_PROGRAMADO, idViaje, 0L, datos);
        SobreEvento<DatosViaje> republicado = sobre(TipoEvento.VIAJE_PROGRAMADO, idViaje, 0L, datos);

        // Act
        publicar(TipoEvento.VIAJE_PROGRAMADO, evento);
        publicar(TipoEvento.VIAJE_PROGRAMADO, evento);
        publicarYEsperar(TipoEvento.VIAJE_PROGRAMADO, republicado);
        esperar("original procesado", () -> eventoProcesado(evento.idEvento()));

        // Assert
        assertAll(
                () -> assertEquals(1, viajes(idViaje)),
                () -> assertEquals(1, jdbc.queryForObject("""
                        SELECT count(*) FROM evento_procesado WHERE id_evento = ? AND consumidor = 'boletos'
                        """, Integer.class, evento.idEvento()))
        );
    }

    @Test
    void viajeActualizado_cuandoTraeVersionVieja_seIgnora() {
        // Arrange
        UUID idViaje = UUID.randomUUID();
        SobreEvento<DatosViaje> nuevo = sobre(TipoEvento.VIAJE_ACTUALIZADO, idViaje, 5L,
                datosViaje(idViaje, EstadoViaje.PENDIENTE_ASIGNACION, manana()));
        SobreEvento<DatosViaje> viejo = sobre(TipoEvento.VIAJE_PROGRAMADO, idViaje, 0L,
                datosViaje(idViaje, EstadoViaje.PROGRAMADO, manana()));

        // Act: Actualizado llega antes que Programado
        publicarYEsperar(TipoEvento.VIAJE_ACTUALIZADO, nuevo);
        publicarYEsperar(TipoEvento.VIAJE_PROGRAMADO, viejo);

        // Assert
        Map<String, Object> fila = viaje(idViaje);
        assertAll(
                () -> assertEquals("PENDIENTE_ASIGNACION", fila.get("estado")),
                () -> assertEquals(5L, fila.get("version"))
        );
    }

    @Test
    void viajeProgramado_cuandoEsAlquilerPrivado_seIgnora() {
        // Arrange
        UUID idViaje = UUID.randomUUID();
        DatosViaje regular = datosViaje(idViaje, EstadoViaje.PROGRAMADO, manana());
        DatosViaje alquiler = new DatosViaje(idViaje, TipoViaje.ALQUILER_PRIVADO, EstadoViaje.PROGRAMADO, null,
                regular.idSucursalOrigen(), regular.nombreOrigen(), regular.nombreDestino(), UUID.randomUUID(),
                regular.fechaHoraSalida(), regular.fechaHoraLlegadaEstimada(), null, regular.bus(), regular.chofer());

        // Act
        publicarYEsperar(TipoEvento.VIAJE_PROGRAMADO, sobre(TipoEvento.VIAJE_PROGRAMADO, idViaje, 0L, alquiler));

        // Assert
        assertEquals(0, viajes(idViaje));
    }

    @Test
    void mensajeInvalido_vaDirectoALaDlq() {
        // Arrange
        String cuerpo = "{ esto no es JSON " + UUID.randomUUID();

        // Act
        publicarCrudo(TipoEvento.VIAJE_PROGRAMADO.llaveRuteo(), cuerpo.getBytes(StandardCharsets.UTF_8));

        // Assert
        Message enDlq = rabbit.receive("boletos.viajes.dlq", 15_000);
        assertNotNull(enDlq, "El mensaje inválido debía llegar a boletos.viajes.dlq");
        assertAll(
                () -> assertEquals(cuerpo, new String(enDlq.getBody(), StandardCharsets.UTF_8)),
                () -> assertTrue(enDlq.getMessageProperties().getHeader("x-motivo-fallo").toString()
                        .startsWith("JSON inválido"))
        );
    }

    @Test
    void viajeEliminado_anulaLasComprasYPublicaUnBoletosAnuladosPorCompra() throws Exception {
        // Arrange
        UUID idViaje = viajeALaVenta();
        UUID comprador = cliente(nitNuevo());
        UUID enLinea = UUID.randomUUID();
        UUID mostrador = UUID.randomUUID();
        carteraAprueba();
        comprar(comprador, enLinea, idViaje, 1, 2).andExpect(status().isCreated());
        mockMvc.perform(conToken(MockMvcRequestBuilders.post("/boletos/ventas-mostrador"),
                        token(UUID.randomUUID(), Rol.CAJERO, SUCURSAL_XELA))
                        .contentType("application/json")
                        .content(json(Map.of("idCompra", mostrador, "idViaje", idViaje, "asientos", List.of(3),
                                "fechaPago", "2026-10-20"))))
                .andExpect(status().isCreated());
        SobreEvento<DatosViajeEliminado> eliminado = sobre(TipoEvento.VIAJE_ELIMINADO, idViaje, 2L,
                new DatosViajeEliminado(idViaje, TipoViaje.REGULAR, null, Instant.now(), UUID.randomUUID()));

        // Act
        publicarYEsperar(TipoEvento.VIAJE_ELIMINADO, eliminado);

        // Assert
        assertAll(
                () -> assertEquals(true, viaje(idViaje).get("eliminado")),
                () -> assertEquals("ANULADA", estadoCompra(enLinea)),
                () -> assertEquals("ANULADA", estadoCompra(mostrador)),
                () -> assertEquals(1, outbox(TipoEvento.BOLETOS_ANULADOS, enLinea)),
                () -> assertEquals(1, outbox(TipoEvento.BOLETOS_ANULADOS, mostrador)),
                () -> assertEquals("VIAJE_ELIMINADO", jdbc.queryForObject("""
                        SELECT datos ->> 'motivo' FROM outbox_evento WHERE tipo_evento = 'BoletosAnulados' AND id_agregado = ?
                        """, String.class, enLinea)),
                () -> assertEquals(TipoEvento.BOLETOS_ANULADOS.llaveRuteo(), jdbc.queryForObject("""
                        SELECT llave_ruteo FROM outbox_evento WHERE tipo_evento = 'BoletosAnulados' AND id_agregado = ?
                        """, String.class, mostrador))
        );
        mockMvc.perform(conToken(MockMvcRequestBuilders.get("/boletos/viajes/" + idViaje + "/asientos"), token(comprador)))
                .andExpect(status().isNotFound());
    }
}
