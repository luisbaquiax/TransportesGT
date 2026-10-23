package org.transportsgt.operationalcostsservice.config.security;

import org.junit.jupiter.api.Test;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.operationalcostsservice.exception.OperacionNoAutorizadaException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoliticaAccesoSucursalTest {

    private final PoliticaAccesoSucursal politica = new PoliticaAccesoSucursal();

    private UsuarioAutenticado sis() {
        return new UsuarioAutenticado(UUID.randomUUID(), "sis@t.gt", Rol.ADMIN_SISTEMA, null, "SIS");
    }

    private UsuarioAutenticado suc(UUID idSucursal) {
        return new UsuarioAutenticado(UUID.randomUUID(), "suc@t.gt", Rol.ADMIN_SUCURSAL, idSucursal, "SUC");
    }

    @Test
    void adminSistemaPasaEnExigirAdminSistema() {
        assertThatCode(() -> politica.exigirAdminSistema(sis(), "x"))
                .doesNotThrowAnyException();
    }

    @Test
    void adminSucursalFallaEnExigirAdminSistema() {
        assertThatThrownBy(() -> politica.exigirAdminSistema(suc(UUID.randomUUID()), "x"))
                .isInstanceOf(OperacionNoAutorizadaException.class);
    }

    @Test
    void sucursalPropiaPasa() {
        UUID mia = UUID.randomUUID();
        assertThatCode(() -> politica.exigirMismaSucursal(suc(mia), mia, "x"))
                .doesNotThrowAnyException();
    }

    @Test
    void sucursalAjenaFalla() {
        assertThatThrownBy(() -> politica.exigirMismaSucursal(suc(UUID.randomUUID()),
                UUID.randomUUID(), "x"))
                .isInstanceOf(OperacionNoAutorizadaException.class);
    }

    @Test
    void recursoSinSucursalFallaParaSUC() {
        assertThatThrownBy(() -> politica.exigirMismaSucursal(suc(UUID.randomUUID()), null, "x"))
                .isInstanceOf(OperacionNoAutorizadaException.class);
    }

    @Test
    void adminSistemaAccedeACualquierSucursal() {
        assertThatCode(() -> politica.exigirMismaSucursal(sis(), UUID.randomUUID(), "x"))
                .doesNotThrowAnyException();
        assertThatCode(() -> politica.exigirMismaSucursal(sis(), null, "x"))
                .doesNotThrowAnyException();
    }
}
