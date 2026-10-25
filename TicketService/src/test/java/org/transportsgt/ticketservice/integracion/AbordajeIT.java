package org.transportsgt.ticketservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.EstadoViaje;
import org.transportsgt.eventos.payload.Rol;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lista de pasajeros y registro del abordaje con el código del boleto.
 */
class AbordajeIT extends IntegracionBase {

    /** Viaje finalizado de los datos iniciales: sus cinco boletos ya abordaron. */
    private static final UUID VIAJE_FINALIZADO = UUID.fromString("50000000-0000-4000-8000-000000000002");

    private final UUID idCajero = UUID.randomUUID();

    private String cajeroXela() {
        return token(idCajero, Rol.CAJERO, SUCURSAL_XELA);
    }

    /** Venta de mostrador de los asientos dados; devuelve los códigos en orden de asiento. */
    private List<String> vender(UUID idViaje, Integer... asientos) throws Exception {
        UUID idCompra = UUID.randomUUID();
        mockMvc.perform(conToken(post("/boletos/ventas-mostrador"), cajeroXela())
                        .contentType("application/json")
                        .content(json(Map.of("idCompra", idCompra, "idViaje", idViaje, "asientos", List.of(asientos),
                                "fechaPago", "2026-10-20"))))
                .andExpect(status().isCreated());
        return jdbc.queryForList("SELECT codigo FROM boleto WHERE id_compra = ? ORDER BY numero_asiento",
                String.class, idCompra);
    }

    private ResultActions abordar(String token, UUID idViaje, String codigo) throws Exception {
        return mockMvc.perform(conToken(post("/boletos/viajes/" + idViaje + "/abordajes"), token)
                .contentType("application/json").content(json(Map.of("codigo", codigo))));
    }

    @Test
    void abordar_registraUnaVezYLaListaLoRefleja() throws Exception {
        // Arrange
        UUID idViaje = viajeALaVenta();
        List<String> codigos = vender(idViaje, 4, 5);

        // Act
        abordar(cajeroXela(), idViaje, " " + codigos.getFirst().toLowerCase() + " ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value(codigos.getFirst()))
                .andExpect(jsonPath("$.numeroAsiento").value(4));
        abordar(cajeroXela(), idViaje, codigos.getFirst()).andExpect(status().isOk());

        // Assert
        mockMvc.perform(conToken(get("/boletos/viajes/" + idViaje + "/pasajeros"), cajeroXela()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boletos").value(2))
                .andExpect(jsonPath("$.abordados").value(1))
                .andExpect(jsonPath("$.pasajeros[0].abordadoEn").isNotEmpty())
                .andExpect(jsonPath("$.pasajeros[1].abordadoEn").isEmpty());
        assertEquals(idCajero, jdbc.queryForObject("SELECT abordado_por FROM boleto WHERE codigo = ?", UUID.class,
                codigos.getFirst()));
    }

    @Test
    void abordar_conReglasQueLoImpiden_respondeElErrorDeCadaCaso() throws Exception {
        // Arrange
        UUID idViaje = viajeALaVenta();
        UUID otroViaje = viajeALaVenta();
        String codigo = vender(idViaje, 8).getFirst();
        String codigoAjeno = vender(otroViaje, 8).getFirst();

        // Act & Assert
        abordar(token(UUID.randomUUID(), Rol.CAJERO, SUCURSAL_CAPITAL), idViaje, codigo)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        abordar(token(UUID.randomUUID()), idViaje, codigo).andExpect(status().isForbidden());
        abordar(cajeroXela(), idViaje, codigoAjeno)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("BOLETO_NO_VALIDO"));
        abordar(cajeroXela(), idViaje, "BOL-NOEXISTE").andExpect(status().isNotFound());
        abordar(cajeroXela(), idViaje, "").andExpect(status().isBadRequest());

        publicarYEsperar(TipoEvento.VIAJE_ACTUALIZADO, sobre(TipoEvento.VIAJE_ACTUALIZADO, idViaje, 1L,
                datosViaje(idViaje, EstadoViaje.EN_TRANSITO, manana())));
        abordar(cajeroXela(), idViaje, codigo)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VIAJE_NO_DISPONIBLE"));
    }

    @Test
    void pasajeros_delViajeFinalizadoDeLaSemilla_yaAbordaron() throws Exception {
        mockMvc.perform(conToken(get("/boletos/viajes/" + VIAJE_FINALIZADO + "/pasajeros"), cajeroXela()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FINALIZADO"))
                .andExpect(jsonPath("$.boletos").value(5))
                .andExpect(jsonPath("$.abordados").value(5));
        mockMvc.perform(conToken(get("/boletos/viajes/" + VIAJE_FINALIZADO + "/pasajeros"),
                        token(UUID.randomUUID(), Rol.ADMIN_SUCURSAL, SUCURSAL_CAPITAL)))
                .andExpect(status().isForbidden());
    }
}
