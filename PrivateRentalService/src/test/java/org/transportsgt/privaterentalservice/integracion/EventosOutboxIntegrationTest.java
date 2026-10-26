package org.transportsgt.privaterentalservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los manejadores de eventos consumen de RabbitMQ (misma topología real) y actualizan alquileres:
 * ViajeProgramado enlaza idViaje, ViajeEliminado cancela, LlegadaRegistrada finaliza.
 */
class EventosOutboxIntegrationTest extends IntegracionBase {

    private String tokenClienteConPerfilPrueba() throws Exception {
        UUID idUsuario = UUID.randomUUID();
        jdbc.update("INSERT INTO cliente_ref (id_usuario, nit, nombre_completo, activo, version) "
                + "VALUES (?, '6666666-6', 'Eventos Prueba', true, 0) ON CONFLICT DO NOTHING", idUsuario);
        return tokenClienteConPerfil(idUsuario, null);
    }

    @Test
    void viajeProgramadoEnlazaIdViajeAlAlquiler() throws Exception {
        String token = tokenClienteConPerfilPrueba();
        String id = solicitar(token);
        cotizar(id, tokenAdminSucursal());
        UUID idAlquiler = UUID.fromString(id);
        UUID idViaje = UUID.randomUUID();

        String sobre = """
                {"idEvento":"%s","tipoEvento":"ViajeProgramado","versionEvento":1,
                 "ocurridoEn":"2026-10-17T12:00:00Z","servicioProductor":"servicio-viajes",
                 "idAgregado":"%s","versionAgregado":1,"idCorrelacion":"%s",
                 "datos":{"idViaje":"%s","tipoViaje":"ALQUILER_PRIVADO","estado":"PROGRAMADO",
                          "idAlquiler":"%s","nombreOrigen":"Quetzaltenango",
                          "nombreDestino":"Ciudad de Guatemala",
                          "fechaHoraSalida":"%s"}}
                """.formatted(UUID.randomUUID(), idViaje, UUID.randomUUID(), idViaje, idAlquiler,
                OffsetDateTime.now().plusDays(5));
        rabbitTemplate.convertAndSend("transporte.eventos", rutaDe("viaje.programado"), sobre);

        esperar(() -> idViaje.equals(jdbc.queryForObject(
                "SELECT id_viaje FROM alquiler WHERE id = ?", UUID.class, idAlquiler)), 15);
    }

    @Test
    void llegadaRegistradaFinalizaAlquiler() throws Exception {
        String token = tokenClienteConPerfilPrueba();
        String id = solicitar(token);
        cotizar(id, tokenAdminSucursal());
        UUID idAlquiler = UUID.fromString(id);

        jdbc.update("UPDATE alquiler SET canal = 'MOSTRADOR' WHERE id = ?", idAlquiler);
        mockMvc.perform(conToken(MockMvcRequestBuilders
                        .post("/alquileres/%s/pago-mostrador".formatted(id)), tokenCajero())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"fechaPago":"%s"}""".formatted(java.time.LocalDate.now()))))
                .andExpect(MockMvcResultMatchers.status().isOk());

        UUID idViaje = UUID.randomUUID();
        String viaje = """
                {"idEvento":"%s","tipoEvento":"ViajeProgramado","versionEvento":1,
                 "ocurridoEn":"2026-10-17T12:00:00Z","servicioProductor":"servicio-viajes",
                 "idAgregado":"%s","versionAgregado":1,"idCorrelacion":"%s",
                 "datos":{"idViaje":"%s","tipoViaje":"ALQUILER_PRIVADO","estado":"PROGRAMADO",
                          "idAlquiler":"%s","nombreOrigen":"X","nombreDestino":"Y",
                          "fechaHoraSalida":"%s"}}
                """.formatted(UUID.randomUUID(), idViaje, UUID.randomUUID(), idViaje, idAlquiler,
                OffsetDateTime.now().plusDays(5));
        rabbitTemplate.convertAndSend("transporte.eventos", rutaDe("viaje.programado"), viaje);
        esperar(() -> idViaje.equals(jdbc.queryForObject(
                "SELECT id_viaje FROM alquiler WHERE id = ?", UUID.class, idAlquiler)), 15);

        String llegada = """
                {"idEvento":"%s","tipoEvento":"LlegadaRegistrada","versionEvento":1,
                 "ocurridoEn":"2026-10-17T18:00:00Z","servicioProductor":"servicio-viajes",
                 "idAgregado":"%s","versionAgregado":1,"idCorrelacion":"%s",
                 "datos":{"idViaje":"%s","tipoViaje":"ALQUILER_PRIVADO","idAlquiler":"%s",
                          "fechaHoraSalidaReal":"%s","fechaHoraLlegadaReal":"%s",
                          "distanciaRecorridaKm":143.00,"gastoCombustibleTotal":350.00}}
                """.formatted(UUID.randomUUID(), idViaje, UUID.randomUUID(), idViaje, idAlquiler,
                OffsetDateTime.now().minusHours(6), OffsetDateTime.now());
        rabbitTemplate.convertAndSend("transporte.eventos", rutaDe("llegada"), llegada);

        esperar(() -> "FINALIZADO".equals(estadoDe(idAlquiler)), 15);
    }

    @Test
    void viajeEliminadoCancelaAlquiler() throws Exception {
        String token = tokenClienteConPerfilPrueba();
        String id = solicitar(token);
        cotizar(id, tokenAdminSucursal());
        UUID idAlquiler = UUID.fromString(id);
        UUID idViaje = UUID.randomUUID();

        String viaje = """
                {"idEvento":"%s","tipoEvento":"ViajeProgramado","versionEvento":1,
                 "ocurridoEn":"2026-10-17T12:00:00Z","servicioProductor":"servicio-viajes",
                 "idAgregado":"%s","versionAgregado":1,"idCorrelacion":"%s",
                 "datos":{"idViaje":"%s","tipoViaje":"ALQUILER_PRIVADO","estado":"PROGRAMADO",
                          "idAlquiler":"%s","nombreOrigen":"X","nombreDestino":"Y",
                          "fechaHoraSalida":"%s"}}
                """.formatted(UUID.randomUUID(), idViaje, UUID.randomUUID(), idViaje, idAlquiler,
                OffsetDateTime.now().plusDays(5));
        rabbitTemplate.convertAndSend("transporte.eventos", rutaDe("viaje.programado"), viaje);
        esperar(() -> idViaje.equals(jdbc.queryForObject(
                "SELECT id_viaje FROM alquiler WHERE id = ?", UUID.class, idAlquiler)), 15);

        String eliminado = """
                {"idEvento":"%s","tipoEvento":"ViajeEliminado","versionEvento":1,
                 "ocurridoEn":"2026-10-17T13:00:00Z","servicioProductor":"servicio-viajes",
                 "idAgregado":"%s","versionAgregado":1,"idCorrelacion":"%s",
                 "datos":{"idViaje":"%s","tipoViaje":"ALQUILER_PRIVADO",
                          "idAlquiler":"%s","eliminadoEn":"2026-10-17T13:00:00Z",
                          "idUsuarioEliminador":"%s"}}
                """.formatted(UUID.randomUUID(), idViaje, UUID.randomUUID(), idViaje, idAlquiler,
                UUID.randomUUID());
        rabbitTemplate.convertAndSend("transporte.eventos", rutaDe("viaje.eliminado"), eliminado);

        esperar(() -> "CANCELADO".equals(estadoDe(idAlquiler)), 20);
        assertThat(jdbc.queryForObject(
                "SELECT motivo_cancelacion FROM alquiler WHERE id = ?", String.class, idAlquiler))
                .isEqualTo("VIAJE_ELIMINADO");
    }
}
