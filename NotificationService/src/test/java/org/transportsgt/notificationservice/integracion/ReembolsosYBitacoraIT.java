package org.transportsgt.notificationservice.integracion;

import org.junit.jupiter.api.Test;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.alquileres.DatosAlquilerCancelado;
import org.transportsgt.eventos.payload.boletos.Canal;
import org.transportsgt.eventos.payload.boletos.DatosBoletosAnulados;
import org.transportsgt.eventos.payload.cartera.DatosCarteraReembolsada;
import org.transportsgt.eventos.payload.costos.TipoReferencia;
import org.transportsgt.eventos.payload.identidad.DatosUsuario;
import org.transportsgt.eventos.payload.identidad.Rol;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Correos de anulaciones, cancelaciones y reembolsos, y la bitácora de notificaciones con sus filtros.
 */
class ReembolsosYBitacoraIT extends IntegracionBase {

    private static final BigDecimal MONTO = new BigDecimal("170.00");

    private SobreEvento<DatosBoletosAnulados> anulados(UUID idComprador, Canal canal, UUID idPago) {
        UUID idCompra = UUID.randomUUID();
        return sobre(TipoEvento.BOLETOS_ANULADOS, idCompra, null, new DatosBoletosAnulados(idCompra, UUID.randomUUID(),
                "VIAJE_ELIMINADO", canal, MONTO, idComprador, idPago, UUID.randomUUID()));
    }

    private SobreEvento<DatosAlquilerCancelado> cancelado(UUID idCliente, UUID idPago) {
        UUID idAlquiler = UUID.randomUUID();
        return sobre(TipoEvento.ALQUILER_CANCELADO, idAlquiler, null, new DatosAlquilerCancelado(idAlquiler, idCliente,
                "Cliente", "1234567K", UUID.randomUUID(), MONTO, idPago, "VIAJE_ELIMINADO", Instant.now()));
    }

    private SobreEvento<DatosCarteraReembolsada> reembolsada(UUID idUsuario, TipoReferencia tipo) {
        UUID idMovimiento = UUID.randomUUID();
        return sobre(TipoEvento.CARTERA_REEMBOLSADA, idMovimiento, null, new DatosCarteraReembolsada(idMovimiento,
                UUID.randomUUID(), idUsuario, MONTO, new BigDecimal("500.00"), tipo, UUID.randomUUID(), "VIAJE_ELIMINADO"));
    }

    @Test
    void anulacionesConYSinCompradorYReembolso() {
        DatosUsuario comprador = contacto(Rol.CLIENTE, null, true);
        var enLinea = anulados(comprador.idUsuario(), Canal.EN_LINEA, UUID.randomUUID());
        var mostrador = anulados(comprador.idUsuario(), Canal.MOSTRADOR, null);
        var sinCuenta = anulados(null, Canal.MOSTRADOR, null);

        publicarYEsperar(TipoEvento.BOLETOS_ANULADOS, enLinea);
        publicarYEsperar(TipoEvento.BOLETOS_ANULADOS, mostrador);
        publicarYEsperar(TipoEvento.BOLETOS_ANULADOS, sinCuenta);
        enviarPendientes();

        assertEquals(1, notificaciones(enLinea.idEvento()));
        assertEquals(1, notificaciones(mostrador.idEvento()));
        assertEquals(0, notificaciones(sinCuenta.idEvento()));
    }

    @Test
    void cancelacionesYReembolsosDeCartera() {
        DatosUsuario cliente = contacto(Rol.CLIENTE, null, true);
        var conPago = cancelado(cliente.idUsuario(), UUID.randomUUID());
        var sinPago = cancelado(cliente.idUsuario(), null);
        var sinCuenta = cancelado(null, null);
        var deAlquiler = reembolsada(cliente.idUsuario(), TipoReferencia.ALQUILER);
        var deBoletos = reembolsada(cliente.idUsuario(), TipoReferencia.BOLETOS);

        publicarYEsperar(TipoEvento.ALQUILER_CANCELADO, conPago);
        publicarYEsperar(TipoEvento.ALQUILER_CANCELADO, sinPago);
        publicarYEsperar(TipoEvento.ALQUILER_CANCELADO, sinCuenta);
        publicarYEsperar(TipoEvento.CARTERA_REEMBOLSADA, deAlquiler);
        publicarYEsperar(TipoEvento.CARTERA_REEMBOLSADA, deBoletos);
        enviarPendientes();

        assertEquals(1, notificaciones(conPago.idEvento()));
        assertEquals(0, notificaciones(sinCuenta.idEvento()));
        assertEquals(1, notificaciones(deBoletos.idEvento()));
    }

    @Test
    void bitacoraConFiltrosYErrores() throws Exception {
        DatosUsuario cliente = contacto(Rol.CLIENTE, null, true);
        var evento = reembolsada(cliente.idUsuario(), TipoReferencia.BOLETOS);
        publicarYEsperar(TipoEvento.CARTERA_REEMBOLSADA, evento);
        UUID idNotificacion = jdbc.queryForObject("SELECT id FROM notificacion WHERE id_evento_origen = ?",
                UUID.class, evento.idEvento());
        String sistema = tokenSistema();

        mockMvc.perform(conToken(get("/notificaciones").param("estado", "PENDIENTE")
                        .param("tipo", "CARTERA_REEMBOLSADA").param("desde", "2026-01-01")
                        .param("hasta", "2030-12-31"), sistema))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(get("/notificaciones").param("desde", "2026-10-31")
                        .param("hasta", "2026-10-01"), sistema))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(get("/notificaciones/" + idNotificacion), sistema)).andExpect(status().isOk());
        mockMvc.perform(conToken(get("/notificaciones/" + UUID.randomUUID()), sistema))
                .andExpect(status().isNotFound());
        // Solo se reintenta una FALLIDA
        mockMvc.perform(conToken(post("/notificaciones/" + idNotificacion + "/reintentar"), sistema))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(conToken(get("/notificaciones").param("estado", "NO_EXISTE"), sistema))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(get("/notificaciones"), token(Rol.CAJERO, UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void licenciasSegunLaSucursalDelUsuario() throws Exception {
        UUID sucursal = UUID.randomUUID();
        String admin = token(Rol.ADMIN_SUCURSAL, sucursal);
        mockMvc.perform(conToken(get("/notificaciones/licencias").param("estado", "PENDIENTE"), admin))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(get("/notificaciones/licencias").param("idSucursal", UUID.randomUUID().toString()),
                        admin))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(get("/notificaciones/licencias"), token(Rol.ADMIN_SUCURSAL, null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(get("/notificaciones/licencias").param("idSucursal", sucursal.toString()),
                        tokenSistema()))
                .andExpect(status().isOk());
    }
}
