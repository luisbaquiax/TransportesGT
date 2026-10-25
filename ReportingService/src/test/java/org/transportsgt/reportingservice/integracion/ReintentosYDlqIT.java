package org.transportsgt.reportingservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.transportsgt.eventos.Productor;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.TopologiaEventos;
import org.transportsgt.reportingservice.fixture.EventoFixture;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Errores transitorios (reintentos con espera creciente) y mensajes venenosos (DLQ inmediata).
 */
class ReintentosYDlqIT extends IntegracionBase {

    @Test
    void boletosAnuladosAntesQueLaVenta_seReintentaYSeAplicaCuandoLlegaLaVenta() throws Exception {
        // Arrange
        UUID idCompra = UUID.randomUUID();
        SobreEvento<?> anulados = sobre(TipoEvento.BOLETOS_ANULADOS, idCompra, null,
                EventoFixture.boletosAnulados(idCompra));
        SobreEvento<?> venta = sobre(TipoEvento.VENTA_BOLETOS_REGISTRADA, idCompra, null, EventoFixture.venta(
                idCompra, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "170.00", 2));

        // Act: la anulación llega primero y falla (transitorio); la venta llega mientras se reintenta
        publicar(TipoEvento.BOLETOS_ANULADOS, anulados);
        Thread.sleep(300);
        publicar(TipoEvento.VENTA_BOLETOS_REGISTRADA, venta);
        esperar("anulación aplicada", () -> eventoProcesado(anulados.idEvento()));

        // Assert
        assertAll(
                () -> assertEquals(Boolean.TRUE, jdbc.queryForObject(
                        "SELECT anulado FROM rep_ingreso WHERE id_referencia = ?", Boolean.class, idCompra)),
                () -> assertEquals(0, jdbc.queryForObject("""
                        SELECT count(*) FROM rep_ingreso WHERE id_referencia = ? AND NOT anulado
                        """, Integer.class, idCompra))
        );
    }

    @Test
    void jsonInvalido_vaALaDlqSinReintentos() {
        // Arrange
        String dlq = TopologiaEventos.colaFallidos("reportes", Productor.COSTOS);
        byte[] basura = "{ esto no es un sobre".getBytes(StandardCharsets.UTF_8);

        // Act
        publicarCrudo(TipoEvento.GASTO_TALLER_REGISTRADO.llaveRuteo(), basura);
        Message enDlq = rabbit.receive(dlq, 15_000);

        // Assert
        assertNotNull(enDlq, "el mensaje debió llegar a " + dlq);
        assertEquals("{ esto no es un sobre", new String(enDlq.getBody(), StandardCharsets.UTF_8));
    }

    @Test
    void eventoSinDatosClave_vaALaDlq() {
        // Arrange
        String dlq = TopologiaEventos.colaFallidos("reportes", Productor.IDENTIDAD);
        SobreEvento<?> sinId = sobre(TipoEvento.SUCURSAL_CREADA, UUID.randomUUID(), 1L,
                EventoFixture.sucursal(null, "Sin id"));

        // Act
        publicar(TipoEvento.SUCURSAL_CREADA, sinId);
        Message enDlq = rabbit.receive(dlq, 15_000);

        // Assert
        assertNotNull(enDlq, "el mensaje debió llegar a " + dlq);
    }

    @Test
    void transitorioQueNuncaSeResuelve_terminaEnLaDlqTrasLosReintentos() {
        // Arrange
        String dlq = TopologiaEventos.colaFallidos("reportes", Productor.VIAJES);
        UUID idRuta = UUID.randomUUID();

        // Act: la ruta nunca llega
        publicar(TipoEvento.RUTA_ELIMINADA, sobre(TipoEvento.RUTA_ELIMINADA, idRuta, null,
                EventoFixture.rutaEliminada(idRuta)));
        Message enDlq = rabbit.receive(dlq, 20_000);

        // Assert
        assertNotNull(enDlq, "el mensaje debió llegar a " + dlq + " tras 5 intentos");
    }
}
