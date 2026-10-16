package org.transportsgt.customerwalletservice.integracion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArranqueIT extends IntegracionBase {

    @Test
    void contexto_cuandoArranca_flywayCreaElEsquemaDelModelo() {
        // Act
        List<String> tablas = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                 WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history' ORDER BY 1
                """, String.class);

        // Assert
        assertEquals(List.of("cartera", "cliente", "evento_procesado", "movimiento_cartera", "outbox_evento"), tablas);
        assertTrue(jdbc.queryForObject("SELECT count(*) FROM pg_trigger WHERE tgname = 'trg_movimiento_inmutable'",
                Integer.class) == 1);
    }
}
