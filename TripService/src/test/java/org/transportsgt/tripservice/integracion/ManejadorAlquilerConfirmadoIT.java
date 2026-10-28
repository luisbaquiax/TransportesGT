package org.transportsgt.tripservice.integracion;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consumidor de AlquilerConfirmado: crea el viaje de alquiler (PENDIENTE_ASIGNACION)
 * y publica ViajeProgramado. Idempotente por idAlquiler.
 */
class ManejadorAlquilerConfirmadoIT extends IntegracionBase {

    private void publicarAlquilerConfirmado(UUID idAlquiler, OffsetDateTime salida) {
        String sobre = """
                {"idEvento":"%s","tipoEvento":"AlquilerConfirmado","versionEvento":1,
                 "ocurridoEn":"2026-10-20T12:00:00Z","servicioProductor":"servicio-alquileres",
                 "idAgregado":"%s","versionAgregado":1,"idCorrelacion":"%s",
                 "datos":{"idAlquiler":"%s",
                          "idUsuarioCliente":"a0000000-0000-4000-8000-000000000001",
                          "nombreCliente":"Cliente Prueba","nit":"1234567-8",
                          "idSucursalOrigen":"%s",
                          "origen":"Quetzaltenango","destino":"San Marcos",
                          "fechaHoraSalida":"%s","fechaHoraRetorno":null,
                          "fechaHoraLlegadaEstimada":"%s",
                          "idaYVuelta":false,"numeroPasajeros":30,
                          "precioTotal":2700.00,
                          "idPago":"b1000000-0000-4000-8000-000000000001",
                          "fechaPago":"2026-10-20"}}
                """.formatted(UUID.randomUUID(), idAlquiler, UUID.randomUUID(),
                idAlquiler, XELA, salida, salida.plusHours(3));
        rabbitTemplate.convertAndSend("transporte.eventos", rutaDe("alquiler.confirmado"), sobre);
    }

    @Test
    void creaViajePendienteYEsIdempotente() {
        UUID idAlquiler = UUID.randomUUID();
        OffsetDateTime salida = OffsetDateTime.now().plusDays(15);

        publicarAlquilerConfirmado(idAlquiler, salida);
        esperar(() -> {
            Integer n = jdbc.queryForObject(
                    "SELECT count(*) FROM viaje WHERE id_alquiler = ?", Integer.class, idAlquiler);
            return n != null && n == 1;
        }, 15);

        // Segunda vez: idempotencia (no duplica)
        publicarAlquilerConfirmado(idAlquiler, salida);
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM viaje WHERE id_alquiler = ?", Integer.class, idAlquiler);
        assertThat(n).isEqualTo(1);

        String estado = jdbc.queryForObject(
                "SELECT estado FROM viaje WHERE id_alquiler = ?", String.class, idAlquiler);
        assertThat(estado).isEqualTo("PENDIENTE_ASIGNACION");

        String tipo = jdbc.queryForObject(
                "SELECT tipo_viaje FROM viaje WHERE id_alquiler = ?", String.class, idAlquiler);
        assertThat(tipo).isEqualTo("ALQUILER_PRIVADO");

        // ViajeProgramado en el outbox
        esperar(() -> {
            UUID idViaje = jdbc.queryForObject(
                    "SELECT id FROM viaje WHERE id_alquiler = ?", UUID.class, idAlquiler);
            return contarOutbox(idViaje, "ViajeProgramado") >= 1;
        }, 15);
    }
}
