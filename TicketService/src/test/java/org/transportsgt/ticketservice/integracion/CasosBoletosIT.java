package org.transportsgt.ticketservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.cartera.DatosPerfilClienteActualizado;
import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.eventos.payload.viajes.DatosViaje;
import org.transportsgt.eventos.payload.viajes.DatosViajeEliminado;
import org.transportsgt.eventos.payload.viajes.EstadoViaje;
import org.transportsgt.eventos.payload.viajes.TipoViaje;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Casos alternos: filtros del catálogo de viajes, validaciones de la compra y de la venta de mostrador, y
 * visibilidad de una compra según el rol.
 */
class CasosBoletosIT extends IntegracionBase {

    private int siguienteAsiento = 20;

    private ResultActions vender(String token, UUID idCompra, UUID idViaje, String nit, String nombre)
            throws Exception {
        return vender(token, idCompra, idViaje, nit, nombre, siguienteAsiento++);
    }

    private ResultActions vender(String token, UUID idCompra, UUID idViaje, String nit, String nombre, int asiento)
            throws Exception {
        Map<String, Object> cuerpo = new HashMap<>(Map.of("idCompra", idCompra, "idViaje", idViaje,
                "asientos", List.of(asiento), "fechaPago", "2026-10-20"));
        if (nit != null) {
            cuerpo.put("nitComprador", nit);
        }
        if (nombre != null) {
            cuerpo.put("nombreComprador", nombre);
        }
        return mockMvc.perform(conToken(post("/boletos/ventas-mostrador"), token)
                .contentType("application/json").content(json(cuerpo)));
    }

    private String cajeroXela() {
        return token(UUID.randomUUID(), Rol.CAJERO, SUCURSAL_XELA);
    }

    private UUID viajeSinBus() {
        UUID idViaje = UUID.randomUUID();
        DatosViaje base = datosViaje(idViaje, EstadoViaje.PROGRAMADO, manana());
        publicarYEsperar(TipoEvento.VIAJE_PROGRAMADO, sobre(TipoEvento.VIAJE_PROGRAMADO, idViaje, 0L,
                new DatosViaje(idViaje, base.tipoViaje(), base.estado(), base.idRuta(), base.idSucursalOrigen(),
                        base.nombreOrigen(), base.nombreDestino(), null, base.fechaHoraSalida(),
                        base.fechaHoraLlegadaEstimada(), base.precioBoleto(), null, null)));
        return idViaje;
    }

    @Test
    void catalogoConFiltros() throws Exception {
        UUID idViaje = viajeALaVenta();
        String idRuta = jdbc.queryForObject("SELECT id_ruta::text FROM viaje_ref WHERE id = ?", String.class, idViaje);
        String cliente = token(UUID.randomUUID());

        mockMvc.perform(conToken(get("/boletos/viajes").param("idOrigen", SUCURSAL_XELA.toString())
                        .param("idRuta", idRuta).param("destino", " guatemala ")
                        .param("fecha", manana().toLocalDate().toString()), cliente))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(get("/boletos/viajes").param("destino", " ").param("idOrigen",
                        UUID.randomUUID().toString()), cliente))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(get("/boletos/viajes/" + viajeSinBus() + "/asientos"), cliente))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void compraConAsientosInvalidos() throws Exception {
        carteraAprueba();
        UUID idViaje = viajeALaVenta();
        UUID idUsuario = cliente(nitNuevo());

        comprar(idUsuario, UUID.randomUUID(), idViaje).andExpect(status().is4xxClientError());
        comprar(idUsuario, UUID.randomUUID(), idViaje, IntStream.rangeClosed(1, 30).boxed().toArray(Integer[]::new))
                .andExpect(status().is4xxClientError());
        comprar(idUsuario, UUID.randomUUID(), idViaje, 0).andExpect(status().is4xxClientError());
        comprar(idUsuario, UUID.randomUUID(), idViaje, 41).andExpect(status().is4xxClientError());
        comprar(idUsuario, UUID.randomUUID(), idViaje, 3, 3).andExpect(status().is4xxClientError());
        comprar(idUsuario, UUID.randomUUID(), viajeSinBus(), 1).andExpect(status().is4xxClientError());
        comprar(idUsuario, UUID.randomUUID(), UUID.randomUUID(), 1).andExpect(status().is4xxClientError());
    }

    @Test
    void compraRepetidaConOtrosDatosYClientesNoValidos() throws Exception {
        carteraAprueba();
        UUID idViaje = viajeALaVenta();
        UUID idUsuario = cliente(nitNuevo());
        UUID idCompra = UUID.randomUUID();

        comprar(idUsuario, idCompra, idViaje, 7).andExpect(status().isCreated());
        comprar(idUsuario, idCompra, idViaje, 7).andExpect(status().is2xxSuccessful());
        comprar(idUsuario, idCompra, idViaje, 8).andExpect(status().isConflict());
        comprar(UUID.randomUUID(), idCompra, idViaje, 7).andExpect(status().is4xxClientError());

        UUID sinNit = UUID.randomUUID();
        publicarYEsperar(TipoEvento.PERFIL_CLIENTE_ACTUALIZADO, sobre(TipoEvento.PERFIL_CLIENTE_ACTUALIZADO, sinNit,
                0L, new DatosPerfilClienteActualizado(sinNit, null, "Sin Nit", true)));
        comprar(sinNit, UUID.randomUUID(), idViaje, 9).andExpect(status().is4xxClientError());
        UUID inactivo = UUID.randomUUID();
        publicarYEsperar(TipoEvento.PERFIL_CLIENTE_ACTUALIZADO, sobre(TipoEvento.PERFIL_CLIENTE_ACTUALIZADO, inactivo,
                0L, new DatosPerfilClienteActualizado(inactivo, nitNuevo(), "Inactivo", false)));
        comprar(inactivo, UUID.randomUUID(), idViaje, 9).andExpect(status().is4xxClientError());
    }

    @Test
    void viajeEliminadoNoSeVende() throws Exception {
        carteraAprueba();
        UUID idViaje = viajeALaVenta();
        publicarYEsperar(TipoEvento.VIAJE_ELIMINADO, sobre(TipoEvento.VIAJE_ELIMINADO, idViaje, 1L,
                new DatosViajeEliminado(idViaje, TipoViaje.REGULAR, null, Instant.now(), UUID.randomUUID())));
        comprar(cliente(nitNuevo()), UUID.randomUUID(), idViaje, 1).andExpect(status().is4xxClientError());
    }

    @Test
    void ventaDeMostradorConNitFinalInvalidoYRepetida() throws Exception {
        UUID idViaje = viajeALaVenta();
        String cajero = cajeroXela();
        UUID idCompra = UUID.randomUUID();

        vender(cajero, UUID.randomUUID(), idViaje, "CF", " ").andExpect(status().isCreated());
        vender(cajero, UUID.randomUUID(), idViaje, "1", "Nombre").andExpect(status().is4xxClientError());
        String nit = nitNuevo();
        vender(cajero, idCompra, idViaje, nit, "Comprador Mostrador", 30).andExpect(status().isCreated());
        // Reintento con la misma idCompra: devuelve la venta existente
        vender(cajero, idCompra, idViaje, nit, "Comprador Mostrador", 30).andExpect(status().is2xxSuccessful());
    }

    @Test
    void visibilidadDeUnaCompraSegunElRol() throws Exception {
        UUID idViaje = viajeALaVenta();
        UUID idCompra = UUID.randomUUID();
        String cajero = cajeroXela();
        vender(cajero, idCompra, idViaje, null, null).andExpect(status().isCreated());

        mockMvc.perform(conToken(get("/boletos/compras/" + idCompra), cajero)).andExpect(status().isOk());
        mockMvc.perform(conToken(get("/boletos/compras/" + idCompra),
                        token(UUID.randomUUID(), Rol.ADMIN_SUCURSAL, SUCURSAL_XELA)))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(get("/boletos/compras/" + idCompra), token(UUID.randomUUID(), Rol.ADMIN_SISTEMA, null)))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(get("/boletos/compras/" + idCompra),
                        token(UUID.randomUUID(), Rol.ADMIN_SUCURSAL, SUCURSAL_CAPITAL)))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(conToken(get("/boletos/compras/" + idCompra), token(UUID.randomUUID())))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(conToken(get("/boletos/compras").param("idViaje", idViaje.toString())
                        .param("estado", "CONFIRMADA"), token(UUID.randomUUID(), Rol.ADMIN_SUCURSAL, SUCURSAL_XELA)))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(get("/boletos/compras/mias").param("estado", "CONFIRMADA"), token(UUID.randomUUID())))
                .andExpect(status().isOk());
    }

    @Test
    void erroresDeLaSolicitud() throws Exception {
        String cliente = token(UUID.randomUUID());
        mockMvc.perform(conToken(get("/boletos/compras/no-es-uuid"), cliente)).andExpect(status().isBadRequest());
        mockMvc.perform(conToken(get("/boletos/compras/" + UUID.randomUUID()), cliente))
                .andExpect(status().isNotFound());
        mockMvc.perform(conToken(post("/boletos/compras"), cliente).contentType("application/json")
                        .content("{no es json"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(post("/boletos/compras"), cliente).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(get("/boletos/viajes").param("fecha", "mañana"), cliente))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(get("/boletos/no-existe"), cliente)).andExpect(status().isNotFound());
    }
}
