package org.transportsgt.ticketservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.transportsgt.eventos.payload.Rol;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Venta de mostrador, permisos por sucursal e historial por NIT.
 */
class VentaMostradorIT extends IntegracionBase {

    private ResultActions vender(String token, UUID idCompra, UUID idViaje, String nit) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>(Map.of("idCompra", idCompra, "idViaje", idViaje,
                "asientos", List.of(15), "fechaPago", "2026-10-20"));
        if (nit != null) {
            cuerpo.put("nitComprador", nit);
            cuerpo.put("nombreComprador", "Comprador de mostrador");
        }
        return mockMvc.perform(conToken(post("/boletos/ventas-mostrador"), token)
                .contentType("application/json").content(json(cuerpo)));
    }

    @Test
    void historial_incluyeLasVentasDeMostradorConSuNitAnterioresALaCuenta() throws Exception {
        // Arrange: el cajero vende con un NIT que todavía no tiene cuenta
        UUID idViaje = viajeALaVenta();
        String nit = nitNuevo();
        UUID idVenta = UUID.randomUUID();
        vender(token(UUID.randomUUID(), Rol.CAJERO, SUCURSAL_XELA), idVenta, idViaje, nit)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.canal").value("MOSTRADOR"))
                .andExpect(jsonPath("$.idPago").isEmpty());
        UUID comprador = cliente(nit);

        // Act & Assert
        mockMvc.perform(conToken(get("/boletos/compras/mias"), token(comprador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].idCompra").value(idVenta.toString()));
        mockMvc.perform(conToken(get("/boletos/compras/" + idVenta), token(comprador)))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(get("/boletos/compras/" + idVenta), token(cliente(nitNuevo()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void vender_enUnViajeDeOtraSucursal_responde403() throws Exception {
        // Arrange
        UUID idViaje = viajeALaVenta();

        // Act & Assert
        vender(token(UUID.randomUUID(), Rol.CAJERO, SUCURSAL_CAPITAL), UUID.randomUUID(), idViaje, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
    }

    @Test
    void vender_comoCliente_responde403() throws Exception {
        // Act & Assert
        vender(token(UUID.randomUUID()), UUID.randomUUID(), UUID.randomUUID(), null)
                .andExpect(status().isForbidden());
    }

    @Test
    void listado_muestraSoloLasComprasDeLaSucursalDelCajero() throws Exception {
        // Arrange
        UUID idViaje = viajeALaVenta();
        UUID idVenta = UUID.randomUUID();
        String cajero = token(UUID.randomUUID(), Rol.ADMIN_SUCURSAL, SUCURSAL_XELA);
        vender(cajero, idVenta, idViaje, null).andExpect(status().isCreated());

        // Act & Assert
        mockMvc.perform(conToken(get("/boletos/compras").param("idViaje", idViaje.toString()), cajero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].nitComprador").value("CF"));
        mockMvc.perform(conToken(get("/boletos/compras").param("idViaje", idViaje.toString()),
                        token(UUID.randomUUID(), Rol.CAJERO, SUCURSAL_CAPITAL)))
                .andExpect(jsonPath("$.totalElementos").value(0));
    }
}
