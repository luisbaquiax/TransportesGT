package org.transportsgt.customerwalletservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.transportsgt.customerwalletservice.dto.request.PagoRequest;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.boletos.Canal;
import org.transportsgt.eventos.payload.alquileres.DatosAlquilerCancelado;
import org.transportsgt.eventos.payload.boletos.DatosBoletosAnulados;
import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.eventos.payload.costos.TipoReferencia;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consumidores de {@code cartera.boletos.q} y {@code cartera.alquileres.q}: reembolsos idempotentes.
 */
class ReembolsoIT extends IntegracionBase {

    private static final LocalDate FECHA = LocalDate.parse("2026-10-21");

    private UUID pagar(UUID idUsuario, TipoReferencia tipo, UUID idReferencia, String monto) throws Exception {
        String respuesta = mockMvc.perform(conToken(post("/cartera/pagos"), token(idUsuario, Rol.CLIENTE))
                        .header(CABECERA_IDEMPOTENCIA, idReferencia.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new PagoRequest(tipo, idReferencia, new BigDecimal(monto), FECHA, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(jsonMapper.readTree(respuesta).get("idPago").asString());
    }

    private SobreEvento<DatosBoletosAnulados> boletosAnulados(UUID idCompra, Canal canal, UUID idPago, UUID idUsuario) {
        return sobre(TipoEvento.BOLETOS_ANULADOS, idCompra, null, new DatosBoletosAnulados(idCompra, UUID.randomUUID(),
                "VIAJE_ELIMINADO", canal, new BigDecimal("170.00"), idUsuario, idPago, UUID.randomUUID()));
    }

    @Test
    void boletosAnulados_cuandoLlegaRepetido_reembolsaUnaSolaVezYPublica() throws Exception {
        // Arrange
        UUID idUsuario = clienteConSaldo("200.00");
        UUID idCompra = UUID.randomUUID();
        UUID idPago = pagar(idUsuario, TipoReferencia.BOLETOS, idCompra, "170.00");
        SobreEvento<DatosBoletosAnulados> evento = boletosAnulados(idCompra, Canal.EN_LINEA, idPago, idUsuario);
        // Boletos puede volver a publicarlo con otro idEvento (caso borde del plan §4.1)
        SobreEvento<DatosBoletosAnulados> otraVez = boletosAnulados(idCompra, Canal.EN_LINEA, idPago, idUsuario);

        // Act
        publicar(TipoEvento.BOLETOS_ANULADOS, evento);
        publicar(TipoEvento.BOLETOS_ANULADOS, evento);
        publicar(TipoEvento.BOLETOS_ANULADOS, otraVez);
        esperar("anulaciones procesadas", () -> eventoProcesado(evento.idEvento()) && eventoProcesado(otraVez.idEvento()));

        // Assert
        Map<String, Object> reembolso = jdbc.queryForMap("""
                SELECT monto, saldo_posterior FROM movimiento_cartera WHERE tipo = 'REEMBOLSO' AND id_referencia = ?
                """, idCompra);
        assertAll(
                () -> assertEquals(1, movimientos(idUsuario, "REEMBOLSO")),
                () -> assertEquals(new BigDecimal("200.00"), saldo(idUsuario)),
                () -> assertEquals(new BigDecimal("170.00"), reembolso.get("monto")),
                () -> assertEquals(new BigDecimal("200.00"), reembolso.get("saldo_posterior")),
                () -> assertEquals(1, jdbc.queryForObject("""
                        SELECT count(*) FROM outbox_evento WHERE tipo_evento = 'CarteraReembolsada'
                           AND llave_ruteo = 'cartera.cartera.reembolsada' AND datos ->> 'idReferencia' = ?
                        """, Integer.class, idCompra.toString()))
        );
    }

    @Test
    void boletosAnulados_cuandoLaCompraFueEnMostrador_seIgnora() {
        // Arrange
        UUID idUsuario = clienteConSaldo("50.00");
        SobreEvento<DatosBoletosAnulados> evento = boletosAnulados(UUID.randomUUID(), Canal.MOSTRADOR, null, null);

        // Act
        publicar(TipoEvento.BOLETOS_ANULADOS, evento);
        esperar("evento procesado", () -> eventoProcesado(evento.idEvento()));

        // Assert
        assertAll(
                () -> assertEquals(0, movimientos(idUsuario, "REEMBOLSO")),
                () -> assertEquals(new BigDecimal("50.00"), saldo(idUsuario))
        );
    }

    @Test
    void boletosAnulados_cuandoNoHuboPago_seIgnoraSinError() {
        // Arrange: compra anulada mientras estaba RESERVADA (nunca se cobró)
        UUID idCompra = UUID.randomUUID();
        SobreEvento<DatosBoletosAnulados> evento = boletosAnulados(idCompra, Canal.EN_LINEA, null, UUID.randomUUID());

        // Act
        publicar(TipoEvento.BOLETOS_ANULADOS, evento);
        esperar("evento procesado", () -> eventoProcesado(evento.idEvento()));

        // Assert
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM movimiento_cartera WHERE id_referencia = ?",
                Integer.class, idCompra));
    }

    @Test
    void alquilerCancelado_cuandoElClienteEstaInactivo_igualSeReembolsa() throws Exception {
        // Arrange
        UUID idUsuario = clienteConSaldo("4000.00");
        UUID idAlquiler = UUID.randomUUID();
        UUID idPago = pagar(idUsuario, TipoReferencia.ALQUILER, idAlquiler, "3800.00");
        jdbc.update("UPDATE cliente SET activo = false WHERE id_usuario = ?", idUsuario);
        Instant cancelado = Instant.parse("2026-10-23T03:00:00Z");
        SobreEvento<DatosAlquilerCancelado> evento = sobre(TipoEvento.ALQUILER_CANCELADO, idAlquiler, null,
                new DatosAlquilerCancelado(idAlquiler, idUsuario, "Cliente", "1234567K", UUID.randomUUID(),
                        new BigDecimal("3800.00"), idPago, "VIAJE_ELIMINADO", cancelado));

        // Act
        publicar(TipoEvento.ALQUILER_CANCELADO, evento);
        esperar("cancelación procesada", () -> eventoProcesado(evento.idEvento()));

        // Assert
        assertAll(
                () -> assertEquals(new BigDecimal("4000.00"), saldo(idUsuario)),
                () -> assertEquals(1, movimientos(idUsuario, "REEMBOLSO")),
                // 03:00 UTC del 23 = 21:00 del 22 en Guatemala
                () -> assertEquals(LocalDate.parse("2026-10-22"), jdbc.queryForObject("""
                        SELECT fecha_movimiento FROM movimiento_cartera WHERE tipo = 'REEMBOLSO' AND id_referencia = ?
                        """, LocalDate.class, idAlquiler))
        );
    }
}
