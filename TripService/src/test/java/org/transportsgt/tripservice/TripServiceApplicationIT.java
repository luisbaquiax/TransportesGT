package org.transportsgt.tripservice;

import org.junit.jupiter.api.Test;
import org.transportsgt.tripservice.integracion.IntegracionBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Arranque del contexto completo (esquema Flyway + semilla V2 + contenedores).
 */
class TripServiceApplicationIT extends IntegracionBase {

    @Test
    void contextoArrancaYLaSemillaEstaCargada() {
        Integer sucursales = jdbc.queryForObject("SELECT count(*) FROM sucursal_ref", Integer.class);
        Integer buses = jdbc.queryForObject("SELECT count(*) FROM bus_ref", Integer.class);
        Integer choferes = jdbc.queryForObject("SELECT count(*) FROM chofer_ref", Integer.class);
        Integer rutas = jdbc.queryForObject("SELECT count(*) FROM ruta", Integer.class);
        Integer viajes = jdbc.queryForObject("SELECT count(*) FROM viaje", Integer.class);
        assertThat(sucursales).isGreaterThanOrEqualTo(3);
        assertThat(buses).isGreaterThanOrEqualTo(3);
        assertThat(choferes).isGreaterThanOrEqualTo(3);
        assertThat(rutas).isGreaterThanOrEqualTo(2);
        assertThat(viajes).isGreaterThanOrEqualTo(3);
    }
}
