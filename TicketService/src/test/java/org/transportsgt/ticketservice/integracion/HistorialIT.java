package org.transportsgt.ticketservice.integracion;

import org.junit.jupiter.api.Test;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.viajes.DatosViajeEliminado;
import org.transportsgt.eventos.payload.viajes.TipoViaje;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /boletos/compras/mias con el filtro opcional {@code estado}.
 */
class HistorialIT extends IntegracionBase {

    @Test
    void mias_filtraPorEstado() throws Exception {
        // Arrange: una compra que sigue CONFIRMADA y otra que se anula al eliminarse su viaje
        UUID comprador = cliente(nitNuevo());
        UUID viajeVigente = viajeALaVenta();
        UUID viajeEliminado = viajeALaVenta();
        UUID confirmada = UUID.randomUUID();
        UUID anulada = UUID.randomUUID();
        carteraAprueba();
        comprar(comprador, confirmada, viajeVigente, 1).andExpect(status().isCreated());
        comprar(comprador, anulada, viajeEliminado, 1).andExpect(status().isCreated());
        SobreEvento<DatosViajeEliminado> eliminado = sobre(TipoEvento.VIAJE_ELIMINADO, viajeEliminado, 2L,
                new DatosViajeEliminado(viajeEliminado, TipoViaje.REGULAR, null, Instant.now(), UUID.randomUUID()));
        publicarYEsperar(TipoEvento.VIAJE_ELIMINADO, eliminado);
        esperar("la compra se anula", () -> "ANULADA".equals(estadoCompra(anulada)));
        String token = token(comprador);

        // Act & Assert
        mockMvc.perform(conToken(get("/boletos/compras/mias"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2));
        mockMvc.perform(conToken(get("/boletos/compras/mias").param("estado", "CONFIRMADA"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].idCompra").value(confirmada.toString()));
        mockMvc.perform(conToken(get("/boletos/compras/mias").param("estado", "ANULADA"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].idCompra").value(anulada.toString()))
                .andExpect(jsonPath("$.contenido[0].motivoAnulacion").exists());
        mockMvc.perform(conToken(get("/boletos/compras/mias").param("estado", "RESERVADA"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void mias_sinNitConFiltro_respondeVacio() throws Exception {
        // Arrange: perfil sin NIT (no puede comprar en línea), consulta por la rama sin NIT
        UUID sinNit = cliente(null);

        // Act & Assert
        mockMvc.perform(conToken(get("/boletos/compras/mias").param("estado", "CONFIRMADA"), token(sinNit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void mias_conEstadoInvalido_responde400() throws Exception {
        // Act & Assert
        mockMvc.perform(conToken(get("/boletos/compras/mias").param("estado", "PAGADA"), token(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }
}
