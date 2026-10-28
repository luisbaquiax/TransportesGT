package org.transportsgt.customerwalletservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.transportsgt.customerwalletservice.dto.request.PagoRequest;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.alquileres.DatosAlquilerCancelado;
import org.transportsgt.eventos.payload.boletos.Canal;
import org.transportsgt.eventos.payload.boletos.DatosBoletosAnulados;
import org.transportsgt.eventos.payload.costos.TipoReferencia;
import org.transportsgt.eventos.payload.identidad.DatosUsuario;
import org.transportsgt.eventos.payload.identidad.Rol;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Eventos con datos incompletos o sin cambios: se descartan o no alteran nada, y el consumidor sigue vivo.
 */
class EventosAlternosIT extends IntegracionBase {

    private static final LocalDate FECHA = LocalDate.parse("2026-10-20");

    /**
     * Un UsuarioCreado válido detrás de los anteriores: cuando se procesa, la cola ya avanzó.
     */
    private void esperarCola() {
        UUID testigo = UUID.randomUUID();
        publicarUsuarioCreado(testigo, true);
    }

    @Test
    void usuarioCreadoIncompletoSeDescarta() {
        UUID sinCorreo = UUID.randomUUID();
        UUID sinNombre = UUID.randomUUID();
        publicar(TipoEvento.USUARIO_CREADO, sobre(TipoEvento.USUARIO_CREADO, sinCorreo, 0L,
                new DatosUsuario(sinCorreo, " ", "Sin Correo", Rol.CLIENTE, null, true)));
        publicar(TipoEvento.USUARIO_CREADO, sobre(TipoEvento.USUARIO_CREADO, sinNombre, 0L,
                new DatosUsuario(sinNombre, "x@transportes.gt", null, Rol.CLIENTE, null, true)));
        publicar(TipoEvento.USUARIO_CREADO, sobre(TipoEvento.USUARIO_CREADO, UUID.randomUUID(), 0L,
                new DatosUsuario(null, "y@transportes.gt", "Sin Id", Rol.CLIENTE, null, true)));
        esperarCola();

        assertEquals(0, clientes(sinCorreo));
        assertEquals(0, clientes(sinNombre));
        // La DLQ es compartida: otros IT esperan encontrar ahí solo su propio mensaje
        while (rabbit.receive("cartera.identidad.dlq", 500) != null) {
            // se descarta
        }
    }

    @Test
    void usuarioActualizadoSinVersionYSinCambios() {
        UUID idUsuario = UUID.randomUUID();
        publicarUsuarioCreado(idUsuario, true);
        String nombre = jdbc.queryForObject("SELECT nombre_completo FROM cliente WHERE id_usuario = ?",
                String.class, idUsuario);
        SobreEvento<DatosUsuario> igual = sobre(TipoEvento.USUARIO_ACTUALIZADO, idUsuario, 4L,
                new DatosUsuario(idUsuario, "u@transportes.gt", nombre, Rol.CLIENTE, null, true));
        SobreEvento<DatosUsuario> sinVersion = sobre(TipoEvento.USUARIO_ACTUALIZADO, idUsuario, null,
                new DatosUsuario(idUsuario, "u@transportes.gt", "Otro Nombre", Rol.CLIENTE, null, false));

        publicar(TipoEvento.USUARIO_ACTUALIZADO, igual);
        esperar("sin cambios procesado", () -> eventoProcesado(igual.idEvento()));
        publicar(TipoEvento.USUARIO_ACTUALIZADO, sinVersion);
        esperarCola();

        assertEquals(1, clientes(idUsuario));
    }

    @Test
    void cancelacionesYAnulacionesIncompletasOSinPago() throws Exception {
        UUID idUsuario = clienteConSaldo("500.00");
        UUID idAlquiler = UUID.randomUUID();
        String respuesta = mockMvc.perform(conToken(post("/cartera/pagos"), token(idUsuario, Rol.CLIENTE))
                        .header(CABECERA_IDEMPOTENCIA, idAlquiler.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new PagoRequest(TipoReferencia.ALQUILER, idAlquiler, new BigDecimal("300.00"),
                                FECHA, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID idPago = UUID.fromString(jsonMapper.readTree(respuesta).get("idPago").asString());

        // Sin fecha de cancelación (usa ocurridoEn) y motivo en blanco (usa el motivo por defecto)
        SobreEvento<DatosAlquilerCancelado> conPago = sobre(TipoEvento.ALQUILER_CANCELADO, idAlquiler, null,
                new DatosAlquilerCancelado(idAlquiler, idUsuario, "Cliente", "1234567K", UUID.randomUUID(),
                        new BigDecimal("300.00"), idPago, " ", null));
        publicar(TipoEvento.ALQUILER_CANCELADO, conPago);
        esperar("cancelación procesada", () -> eventoProcesado(conPago.idEvento()));
        assertEquals(new BigDecimal("500.00"), saldo(idUsuario));

        // Sin pago (pago en efectivo en mostrador) y sin id de alquiler
        UUID otroAlquiler = UUID.randomUUID();
        publicar(TipoEvento.ALQUILER_CANCELADO, sobre(TipoEvento.ALQUILER_CANCELADO, otroAlquiler, null,
                new DatosAlquilerCancelado(otroAlquiler, idUsuario, "Cliente", "1234567K", UUID.randomUUID(),
                        new BigDecimal("300.00"), null, "VIAJE_ELIMINADO", null)));
        publicar(TipoEvento.ALQUILER_CANCELADO, sobre(TipoEvento.ALQUILER_CANCELADO, UUID.randomUUID(), null,
                new DatosAlquilerCancelado(null, idUsuario, "Cliente", "1234567K", UUID.randomUUID(),
                        new BigDecimal("300.00"), idPago, "VIAJE_ELIMINADO", null)));
        publicar(TipoEvento.BOLETOS_ANULADOS, sobre(TipoEvento.BOLETOS_ANULADOS, UUID.randomUUID(), null,
                new DatosBoletosAnulados(null, UUID.randomUUID(), "VIAJE_ELIMINADO", Canal.EN_LINEA,
                        new BigDecimal("10.00"), idUsuario, idPago, UUID.randomUUID())));
        // Pago de otra referencia: no corresponde a la cancelación
        UUID ajeno = UUID.randomUUID();
        SobreEvento<DatosAlquilerCancelado> cruzado = sobre(TipoEvento.ALQUILER_CANCELADO, ajeno, null,
                new DatosAlquilerCancelado(ajeno, idUsuario, "Cliente", "1234567K", UUID.randomUUID(),
                        new BigDecimal("300.00"), idPago, "VIAJE_ELIMINADO", null));
        publicar(TipoEvento.ALQUILER_CANCELADO, cruzado);
        esperarCola();

        assertEquals(new BigDecimal("500.00"), saldo(idUsuario));
    }

    @Test
    void consultaDePagoInexistente() throws Exception {
        UUID idUsuario = clienteConSaldo("0.00");
        mockMvc.perform(conToken(get("/cartera/pagos").param("tipoReferencia", "BOLETOS")
                        .param("idReferencia", UUID.randomUUID().toString()), token(idUsuario, Rol.CLIENTE)))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(conToken(get("/cartera/pagos").param("tipoReferencia", "BOLETOS"),
                        token(idUsuario, Rol.CLIENTE)))
                .andExpect(status().isBadRequest());
    }
}
