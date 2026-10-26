package org.transportsgt.ticketservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArranqueIT extends IntegracionBase {

    @Test
    void contexto_cuandoArranca_flywayCreaElEsquemaDelModelo() {
        // Act
        List<String> tablas = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                 WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history' ORDER BY 1
                """, String.class);
        List<String> triggers = jdbc.queryForList("""
                SELECT tgname FROM pg_trigger WHERE NOT tgisinternal ORDER BY 1
                """, String.class);

        // Assert
        assertAll(
                () -> assertEquals(List.of("boleto", "cliente_ref", "compra", "evento_procesado", "outbox_evento",
                        "viaje_ref"), tablas),
                () -> assertEquals(List.of("trg_boleto_transicion", "trg_boleto_validar_asiento", "trg_compra_transicion"),
                        triggers)
        );
    }

    @Test
    void triggerDeAsiento_rechazaUnAsientoMayorQueLaCapacidadDelBus() {
        // Arrange
        UUID idViaje = viajeALaVenta();
        UUID idCompra = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO compra (id, id_viaje, canal, cantidad_boletos, precio_unitario, monto_total,
                                    nombre_comprador, id_sucursal_ingreso, id_usuario_vendedor)
                VALUES (?, ?, 'MOSTRADOR', 1, 85, 85, 'Consumidor final', ?, ?)
                """, idCompra, idViaje, SUCURSAL_XELA, UUID.randomUUID());

        // Act
        DataAccessException ex = assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO boleto (id_compra, id_viaje, numero_asiento, codigo) VALUES (?, ?, 41, ?)",
                idCompra, idViaje, "BOL-T" + idCompra.toString().substring(0, 8)));

        // Assert
        assertTrue(ex.getMessage().contains("excede la capacidad del bus (40)"), ex.getMessage());
    }
}
