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
        // Tres choferes enlazados a sus usuarios CHOFER de Identidad (docs/datos-semilla.md)
        Integer enlazados = jdbc.queryForObject(
                "SELECT count(*) FROM chofer WHERE id_usuario IS NOT NULL AND numero_licencia LIKE 'LIC-GT-10_'",
                Integer.class);
        // Crecientes: otras pruebas de integración comparten este mismo contenedor
        assertThat(buses).isGreaterThanOrEqualTo(9);
        assertThat(choferes).isGreaterThanOrEqualTo(9);
        assertThat(enlazados).isEqualTo(3);
        Integer placaSemilla = jdbc.queryForObject(
                "SELECT count(*) FROM bus WHERE placa = 'P900ABC'", Integer.class);
        assertThat(placaSemilla).isEqualTo(1);
    }
}
