package org.transportsgt.privaterentalservice;

import org.junit.jupiter.api.Test;
import org.transportsgt.privaterentalservice.integracion.IntegracionBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Arranque del contexto completo (esquema Flyway + semilla V2 + contenedores).
 */
class PrivateRentalServiceApplicationIT extends IntegracionBase {

    @Test
    void contextoArrancaYLaSemillaEstaCargada() {
        Integer sucursales = jdbc.queryForObject("SELECT count(*) FROM sucursal_ref", Integer.class);
        Integer clientes = jdbc.queryForObject("SELECT count(*) FROM cliente_ref", Integer.class);
        Integer tarifas = jdbc.queryForObject("SELECT count(*) FROM tarifa_alquiler", Integer.class);
        assertThat(sucursales).isGreaterThanOrEqualTo(3);
        assertThat(clientes).isGreaterThanOrEqualTo(2);
        assertThat(tarifas).isGreaterThanOrEqualTo(1);
    }
}
