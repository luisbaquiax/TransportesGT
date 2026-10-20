package org.transportsgt.ticketservice.integracion;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /boletos/viajes/{id}/asiento-maximo}: JWT o {@code X-Clave-Interna} (Viajes, al cambiar el bus).
 */
class AsientoMaximoIT extends IntegracionBase {

    private String ruta(UUID idViaje) {
        return "/boletos/viajes/" + idViaje + "/asiento-maximo";
    }

    @Test
    void asientoMaximo_conJwt_devuelveElMayorAsientoVendido() throws Exception {
        // Arrange
        UUID idViaje = viajeALaVenta();
        UUID comprador = cliente(nitNuevo());
        carteraAprueba();
        comprar(comprador, UUID.randomUUID(), idViaje, 7, 31).andExpect(status().isCreated());

        // Act & Assert
        mockMvc.perform(conToken(get(ruta(idViaje)), token(comprador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asientoMaximo").value(31));
    }

    @Test
    void asientoMaximo_conLaClaveInternaCorrecta_responde200() throws Exception {
        // Act & Assert
        mockMvc.perform(get(ruta(UUID.randomUUID())).header("X-Clave-Interna", CLAVE_INTERNA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asientoMaximo").value(0));
    }

    @Test
    void asientoMaximo_conClaveIncorrectaOSinCredencial_responde401() throws Exception {
        // Act & Assert
        mockMvc.perform(get(ruta(UUID.randomUUID())).header("X-Clave-Interna", "otra"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mockMvc.perform(get(ruta(UUID.randomUUID()))).andExpect(status().isUnauthorized());
    }

    @Test
    void claveInterna_enOtraRuta_noAutentica() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/boletos/viajes/" + UUID.randomUUID() + "/asientos").header("X-Clave-Interna", CLAVE_INTERNA))
                .andExpect(status().isUnauthorized());
    }
}
