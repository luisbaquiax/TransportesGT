package org.transportsgt.fleetservice.services.autorizacion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.fleetservice.config.security.UsuarioAutenticado;
import org.transportsgt.fleetservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.fleetservice.exception.RegistroModificadoException;
import org.transportsgt.fleetservice.exception.SucursalInactivaException;
import org.transportsgt.fleetservice.exception.SucursalNoEncontradaException;
import org.transportsgt.fleetservice.repositories.SucursalRefRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutorizacionSucursalTest {

    @Mock
    private SucursalRefRepository sucursalRefRepository;

    private AutorizacionSucursal autorizacion;

    private final UUID sucursalPropia = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private final UUID sucursalAjena = UUID.fromString("10000000-0000-4000-8000-000000000002");

    @BeforeEach
    void setUp() {
        autorizacion = new AutorizacionSucursal(sucursalRefRepository);
    }

    private static UsuarioAutenticado usuario(Rol rol, UUID idSucursal) {
        return new UsuarioAutenticado(UUID.randomUUID(), "correo@transportes.gt", rol, idSucursal, "Prueba");
    }

    @Test
    void adminSistemaPuedeGestionarCualquierSucursal() {
        assertDoesNotThrow(() -> autorizacion.verificarGestion(
                usuario(Rol.ADMIN_SISTEMA, null), sucursalAjena, "bus"));
    }

    @Test
    void adminSucursalGestionaLoSuyo() {
        assertDoesNotThrow(() -> autorizacion.verificarGestion(
                usuario(Rol.ADMIN_SUCURSAL, sucursalPropia), sucursalPropia, "bus"));
    }

    @Test
    void adminSucursalNoGestionaSucursalAjena() {
        assertThrows(OperacionNoAutorizadaException.class, () -> autorizacion.verificarGestion(
                usuario(Rol.ADMIN_SUCURSAL, sucursalPropia), sucursalAjena, "chofer"));
    }

    @Test
    void otroRolNoGestiona() {
        assertThrows(OperacionNoAutorizadaException.class, () -> autorizacion.verificarGestion(
                usuario(Rol.CLIENTE, sucursalPropia), sucursalPropia, "bus"));
    }

    @Test
    void tokenSinSucursalNoPuedeCrear() {
        assertThrows(OperacionNoAutorizadaException.class,
                () -> autorizacion.validarSucursalGestionada(null));
    }

    @Test
    void sucursalInexistenteLanza404() {
        when(sucursalRefRepository.existsById(sucursalPropia)).thenReturn(false);
        assertThrows(SucursalNoEncontradaException.class,
                () -> autorizacion.validarSucursalGestionada(sucursalPropia));
    }

    @Test
    void sucursalInactivaLanza409() {
        when(sucursalRefRepository.existsById(sucursalPropia)).thenReturn(true);
        when(sucursalRefRepository.existsByIdAndActivaTrue(sucursalPropia)).thenReturn(false);
        assertThrows(SucursalInactivaException.class,
                () -> autorizacion.validarSucursalGestionada(sucursalPropia));
    }

    @Test
    void sucursalActivaPasa() {
        when(sucursalRefRepository.existsById(sucursalPropia)).thenReturn(true);
        when(sucursalRefRepository.existsByIdAndActivaTrue(sucursalPropia)).thenReturn(true);
        assertDoesNotThrow(() -> autorizacion.validarSucursalGestionada(sucursalPropia));
    }

    @Test
    void versionViejaLanzaConflicto() {
        assertThrows(RegistroModificadoException.class,
                () -> autorizacion.verificarVersion(1L, 2L));
    }

    @Test
    void versionNulaOIgnoradaPasa() {
        assertDoesNotThrow(() -> autorizacion.verificarVersion(null, 2L));
        assertDoesNotThrow(() -> autorizacion.verificarVersion(2L, 2L));
    }
}
