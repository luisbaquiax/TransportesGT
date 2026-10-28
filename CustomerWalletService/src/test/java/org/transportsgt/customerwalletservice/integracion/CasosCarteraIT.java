package org.transportsgt.customerwalletservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.transportsgt.customerwalletservice.dto.request.ActualizarPerfilRequest;
import org.transportsgt.customerwalletservice.dto.request.RecargaRequest;
import org.transportsgt.eventos.payload.identidad.Rol;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Casos alternos del perfil, la búsqueda por NIT, las recargas (clave de idempotencia) y los movimientos.
 */
class CasosCarteraIT extends IntegracionBase {

    private static final LocalDate FECHA = LocalDate.parse("2026-10-20");

    private static String nitDe(UUID idUsuario) {
        return "88" + idUsuario.toString().substring(0, 6).replaceAll("[^0-9]", "2") + "-5";
    }

    private static String dpiDe(UUID idUsuario) {
        return ("9" + Math.abs(idUsuario.getMostSignificantBits())).substring(0, 13);
    }

    private ResultActions perfil(String token, ActualizarPerfilRequest request) throws Exception {
        return mockMvc.perform(conToken(put("/clientes/yo"), token)
                .contentType(MediaType.APPLICATION_JSON).content(json(request)));
    }

    private ResultActions recargar(String token, String clave, String monto) throws Exception {
        var peticion = conToken(post("/cartera/recargas"), token).contentType(MediaType.APPLICATION_JSON)
                .content(json(new RecargaRequest(new BigDecimal(monto), FECHA)));
        return mockMvc.perform(clave == null ? peticion : peticion.header(CABECERA_IDEMPOTENCIA, clave));
    }

    @Test
    void perfilConDpiSinNitYRepetidoSinCambios() throws Exception {
        UUID idUsuario = UUID.randomUUID();
        publicarUsuarioCreado(idUsuario, true);
        String token = token(idUsuario, Rol.CLIENTE);
        String nit = nitDe(idUsuario);

        perfil(token, new ActualizarPerfilRequest(null, dpiDe(idUsuario), null, " ")).andExpect(status().isOk());
        perfil(token, new ActualizarPerfilRequest(nit, null, null, null)).andExpect(status().isOk());
        perfil(token, new ActualizarPerfilRequest(nit, dpiDe(idUsuario), "55550000", "Zona 3")).andExpect(status().isOk());

        UUID otro = UUID.randomUUID();
        publicarUsuarioCreado(otro, true);
        perfil(token(otro, Rol.CLIENTE), new ActualizarPerfilRequest(null, dpiDe(idUsuario), null, null))
                .andExpect(status().isConflict());
    }

    @Test
    void buscarPorNitParaPersonalDeSucursal() throws Exception {
        UUID cliente = clienteConSaldo("10.00");
        String nit = jdbc.queryForObject("SELECT nit FROM cliente WHERE id_usuario = ?", String.class, cliente);
        String cajero = token(UUID.randomUUID(), Rol.CAJERO);

        mockMvc.perform(conToken(get("/clientes/buscar").param("nit", nit), cajero)).andExpect(status().isOk());
        mockMvc.perform(conToken(get("/clientes/buscar").param("nit", "1234567-8"), cajero))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(get("/clientes/buscar").param("nit", " "), cajero))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(conToken(get("/clientes/buscar").param("nit", nit), token(cliente, Rol.CLIENTE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void recargaSinClaveConClaveLargaOReutilizada() throws Exception {
        UUID idUsuario = UUID.randomUUID();
        publicarUsuarioCreado(idUsuario, true);
        String token = token(idUsuario, Rol.CLIENTE);
        String clave = UUID.randomUUID().toString();

        recargar(token, null, "50.00").andExpect(status().isBadRequest());
        recargar(token, " ", "50.00").andExpect(status().isBadRequest());
        recargar(token, "x".repeat(101), "50.00").andExpect(status().isBadRequest());
        recargar(token, clave, "50.00").andExpect(status().isCreated());
        recargar(token, clave, "60.00").andExpect(status().isConflict());
        recargar(token, UUID.randomUUID().toString(), "0.00").andExpect(status().isBadRequest());
    }

    @Test
    void movimientosConFiltrosEIntervaloInvalido() throws Exception {
        UUID idUsuario = UUID.randomUUID();
        publicarUsuarioCreado(idUsuario, true);
        String token = token(idUsuario, Rol.CLIENTE);
        recargar(token, UUID.randomUUID().toString(), "25.00").andExpect(status().isCreated());

        mockMvc.perform(conToken(get("/cartera/yo/movimientos").param("tipo", "RECARGA")
                        .param("desde", "2026-10-01").param("hasta", "2026-10-31"), token))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(get("/cartera/yo/movimientos").param("desde", "2026-10-31")
                        .param("hasta", "2026-10-01"), token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(get("/cartera/yo/movimientos").param("tipo", "OTRO"), token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(get("/cartera/yo/movimientos").param("tamano", "500"), token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clienteInexistenteYErroresDeLaSolicitud() throws Exception {
        String sinCliente = token(UUID.randomUUID(), Rol.CLIENTE);
        mockMvc.perform(conToken(get("/clientes/yo"), sinCliente)).andExpect(status().isNotFound());
        mockMvc.perform(conToken(get("/cartera/yo"), sinCliente)).andExpect(status().isNotFound());
        mockMvc.perform(conToken(post("/cartera/recargas"), sinCliente)
                        .header(CABECERA_IDEMPOTENCIA, "k").contentType(MediaType.APPLICATION_JSON)
                        .content("{no es json"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(delete("/cartera/yo"), sinCliente)).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(conToken(get("/clientes/no-existe/x"), sinCliente)).andExpect(status().isNotFound());
        mockMvc.perform(get("/cartera/yo")).andExpect(status().isUnauthorized());
    }
}
