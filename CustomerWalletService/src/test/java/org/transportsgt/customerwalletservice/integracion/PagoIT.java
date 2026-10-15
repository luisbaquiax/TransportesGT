package org.transportsgt.customerwalletservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.transportsgt.customerwalletservice.dto.request.PagoRequest;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.eventos.payload.TipoReferencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PagoIT extends IntegracionBase {

    private static final LocalDate FECHA = LocalDate.parse("2026-10-21");

    private int pagar(String token, UUID idReferencia, String monto) throws Exception {
        return mockMvc.perform(conToken(post("/cartera/pagos"), token)
                        .header(CABECERA_IDEMPOTENCIA, idReferencia.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new PagoRequest(TipoReferencia.BOLETOS, idReferencia, new BigDecimal(monto),
                                FECHA, "boletos"))))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void pago_cuandoDosPagosSimultaneosYElSaldoAlcanzaParaUno_unoPasaYElOtroResponde422() throws Exception {
        // Arrange
        UUID idUsuario = clienteConSaldo("100.00");
        String token = token(idUsuario, Rol.CLIENTE);
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        List<Future<Integer>> resultados = new ArrayList<>();

        // Act
        for (int i = 0; i < 2; i++) {
            UUID idCompra = UUID.randomUUID();
            resultados.add(hilos.submit(() -> {
                salida.await();
                return pagar(token, idCompra, "70.00");
            }));
        }
        salida.countDown();
        List<Integer> estados = new ArrayList<>();
        for (Future<Integer> resultado : resultados) {
            estados.add(resultado.get());
        }
        hilos.shutdown();

        // Assert
        assertAll(
                () -> assertEquals(List.of(201, 422), estados.stream().sorted().toList()),
                () -> assertEquals(new BigDecimal("30.00"), saldo(idUsuario)),
                () -> assertEquals(1, movimientos(idUsuario, "PAGO"))
        );
    }

    @Test
    void pago_cuandoSeRepiteLaMismaClave_cobraUnaVezYResponde200() throws Exception {
        // Arrange
        UUID idUsuario = clienteConSaldo("500.00");
        String token = token(idUsuario, Rol.CLIENTE);
        UUID idCompra = UUID.randomUUID();
        String cuerpo = json(new PagoRequest(TipoReferencia.BOLETOS, idCompra, new BigDecimal("170.00"), FECHA, null));

        // Act
        String idPago = jsonMapper.readTree(mockMvc.perform(conToken(post("/cartera/pagos"), token)
                        .header(CABECERA_IDEMPOTENCIA, idCompra.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.endsWith("/cartera/pagos?tipoReferencia=BOLETOS&idReferencia=" + idCompra)))
                .andExpect(jsonPath("$.saldoPosterior").value("330.00"))
                .andReturn().getResponse().getContentAsString()).get("idPago").asString();
        mockMvc.perform(conToken(post("/cartera/pagos"), token)
                        .header(CABECERA_IDEMPOTENCIA, idCompra.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPago").value(idPago));

        // Assert
        assertAll(
                () -> assertEquals(1, movimientos(idUsuario, "PAGO")),
                () -> assertEquals(new BigDecimal("330.00"), saldo(idUsuario))
        );
    }

    @Test
    void pago_cuandoElPerfilNoTieneNit_responde422PerfilIncompleto() throws Exception {
        // Arrange
        UUID idUsuario = UUID.randomUUID();
        publicarUsuarioCreado(idUsuario, true);

        // Act & Assert
        mockMvc.perform(conToken(post("/cartera/pagos"), token(idUsuario, Rol.CLIENTE))
                        .header(CABECERA_IDEMPOTENCIA, idUsuario.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new PagoRequest(TipoReferencia.ALQUILER, idUsuario, BigDecimal.ONE, FECHA, null))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PERFIL_INCOMPLETO"));
    }

    @Test
    void pago_cuandoLaClaveNoEsLaReferencia_responde400() throws Exception {
        // Arrange
        UUID idUsuario = clienteConSaldo("10.00");

        // Act & Assert
        mockMvc.perform(conToken(post("/cartera/pagos"), token(idUsuario, Rol.CLIENTE))
                        .header(CABECERA_IDEMPOTENCIA, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new PagoRequest(TipoReferencia.BOLETOS, UUID.randomUUID(), BigDecimal.ONE,
                                FECHA, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    void consultarPago_conClaveInternaCorrecta_devuelveElPagoYConIncorrectaResponde401() throws Exception {
        // Arrange
        UUID idUsuario = clienteConSaldo("100.00");
        UUID idCompra = UUID.randomUUID();
        assertEquals(201, pagar(token(idUsuario, Rol.CLIENTE), idCompra, "40.00"));

        // Act & Assert
        mockMvc.perform(get("/cartera/pagos").param("tipoReferencia", "BOLETOS").param("idReferencia", idCompra.toString())
                        .header("X-Clave-Interna", CLAVE_INTERNA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monto").value("40.00"))
                .andExpect(jsonPath("$.fechaPago").value("2026-10-21"));
        mockMvc.perform(get("/cartera/pagos").param("tipoReferencia", "BOLETOS").param("idReferencia", idCompra.toString())
                        .header("X-Clave-Interna", "clave-equivocada"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mockMvc.perform(get("/cartera/pagos").param("tipoReferencia", "BOLETOS")
                        .param("idReferencia", UUID.randomUUID().toString())
                        .header("X-Clave-Interna", CLAVE_INTERNA))
                .andExpect(status().isNotFound());
    }

    @Test
    void consultarPago_conJwt_soloVeLosPagosDeSuCartera() throws Exception {
        // Arrange
        UUID dueno = clienteConSaldo("100.00");
        UUID idCompra = UUID.randomUUID();
        assertEquals(201, pagar(token(dueno, Rol.CLIENTE), idCompra, "10.00"));

        // Act & Assert
        mockMvc.perform(conToken(get("/cartera/pagos").param("tipoReferencia", "BOLETOS")
                        .param("idReferencia", idCompra.toString()), token(dueno, Rol.CLIENTE)))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(get("/cartera/pagos").param("tipoReferencia", "BOLETOS")
                        .param("idReferencia", idCompra.toString()), token(UUID.randomUUID(), Rol.CLIENTE)))
                .andExpect(status().isNotFound());
    }

    @Test
    void pago_conClaveInternaEnLugarDeJwt_responde401() throws Exception {
        // Act & Assert: la clave interna solo vale en GET /cartera/pagos
        UUID idCompra = UUID.randomUUID();
        mockMvc.perform(post("/cartera/pagos").header("X-Clave-Interna", CLAVE_INTERNA)
                        .header(CABECERA_IDEMPOTENCIA, idCompra.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new PagoRequest(TipoReferencia.BOLETOS, idCompra, BigDecimal.ONE, FECHA, null))))
                .andExpect(status().isUnauthorized());
    }
}
