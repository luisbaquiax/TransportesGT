package org.transportsgt.fleetservice;

import org.junit.jupiter.api.Test;
import org.transportsgt.fleetservice.integracion.IntegracionBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Arranque del contexto completo (esquema Flyway + semilla V2 + contenedores),
 * igual que Identidad: extiende IntegracionBase para no tocar la BD local.
 */
class FleetServiceApplicationTests extends IntegracionBase {

    @Test
    void contextoArrancaYLaSemillaEstaCargada() {
        Integer buses = jdbc.queryForObject("SELECT count(*) FROM bus", Integer.class);
        Integer choferes = jdbc.queryForObject("SELECT count(*) FROM chofer", Integer.class);
        Integer eventos = jdbc.queryForObject("SELECT count(*) FROM outbox_evento", Integer.class);
        // Crecientes: otras pruebas de integración comparten este mismo contenedor
        assertThat(buses).isGreaterThanOrEqualTo(9);
        assertThat(choferes).isGreaterThanOrEqualTo(9);
        assertThat(eventos).isGreaterThanOrEqualTo(18);
        Integer placaSemilla = jdbc.queryForObject(
                "SELECT count(*) FROM bus WHERE placa = 'P900ABC'", Integer.class);
        assertThat(placaSemilla).isEqualTo(1);
    }
}
