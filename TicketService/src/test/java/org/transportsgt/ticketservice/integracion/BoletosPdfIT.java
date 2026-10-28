package org.transportsgt.ticketservice.integracion;

import org.junit.jupiter.api.Test;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.viajes.DatosViajeEliminado;
import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.eventos.payload.viajes.TipoViaje;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /boletos/compras/{id}/pdf.
 */
class BoletosPdfIT extends IntegracionBase {

    /** Compra en línea de Carlos Méndez en los datos iniciales (asientos 5 y 6). */
    private static final UUID COMPRA_SEMILLA = UUID.fromString("d0000000-0000-4000-8000-000000000001");
    private static final UUID CARLOS = UUID.fromString("00000000-0000-4000-8000-000000000031");

    @Test
    void pdf_delComprador_esUnAdjuntoPdf() throws Exception {
        byte[] pdf = mockMvc.perform(conToken(get("/boletos/compras/" + COMPRA_SEMILLA + "/pdf"), token(CARLOS)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"boletos-" + COMPRA_SEMILLA + ".pdf\""))
                .andReturn().getResponse().getContentAsByteArray();

        assertEquals("%PDF", new String(pdf, 0, 4));
    }

    @Test
    void pdf_paraElMostradorDeLaSucursal_yNoParaOtroCliente() throws Exception {
        mockMvc.perform(conToken(get("/boletos/compras/" + COMPRA_SEMILLA + "/pdf"),
                        token(UUID.randomUUID(), Rol.CAJERO, SUCURSAL_XELA)))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(get("/boletos/compras/" + COMPRA_SEMILLA + "/pdf"), token(cliente(nitNuevo()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    void pdf_deUnaCompraAnulada_responde409() throws Exception {
        // Arrange
        UUID comprador = cliente(nitNuevo());
        UUID idViaje = viajeALaVenta();
        UUID idCompra = UUID.randomUUID();
        carteraAprueba();
        comprar(comprador, idCompra, idViaje, 3).andExpect(status().isCreated());
        SobreEvento<DatosViajeEliminado> eliminado = sobre(TipoEvento.VIAJE_ELIMINADO, idViaje, 2L,
                new DatosViajeEliminado(idViaje, TipoViaje.REGULAR, null, Instant.now(), UUID.randomUUID()));
        publicarYEsperar(TipoEvento.VIAJE_ELIMINADO, eliminado);
        esperar("la compra se anula", () -> "ANULADA".equals(estadoCompra(idCompra)));

        // Act & Assert
        mockMvc.perform(conToken(get("/boletos/compras/" + idCompra + "/pdf"), token(comprador)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("COMPRA_NO_CONFIRMADA"));
    }
}
