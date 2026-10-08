package org.transportsgt.identityservice;

import org.junit.jupiter.api.Test;
import org.transportsgt.identityservice.integracion.IntegracionBase;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdentityServiceApplicationTests extends IntegracionBase {

    @Test
    void contextLoads() {
        // Flyway aplicó V1 y V2: 3 sucursales del seed
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM sucursal WHERE id::text LIKE '10000000-%'",
                Integer.class));
    }
}
