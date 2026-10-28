package org.transportsgt.tripservice.services.autorizacion;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.tripservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.tripservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.tripservice.models.ChoferRef;
import org.transportsgt.tripservice.models.Ruta;
import org.transportsgt.tripservice.models.Viaje;
import org.transportsgt.tripservice.repositories.ChoferRefRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutorizacionViajeTest {

    private static final UUID SUCURSAL = UUID.randomUUID();
    private static final UUID OTRA_SUCURSAL = UUID.randomUUID();
    private static final UUID ID_CHOFER = UUID.randomUUID();

    @Mock
    private ChoferRefRepository choferRefRepository;

    @InjectMocks
    private AutorizacionViaje autorizacion;

    private static UsuarioAutenticado usuario(Rol rol, UUID idSucursal) {
        return new UsuarioAutenticado(UUID.randomUUID(), "u@transportes.gt", rol, idSucursal, "Usuario");
    }

    private static Viaje viaje(UUID idSucursal, UUID idChofer) {
        return Viaje.builder().id(UUID.randomUUID()).idSucursalOrigen(idSucursal).idChofer(idChofer).build();
    }

    private void choferEnlazadoA(UsuarioAutenticado usuario) {
        when(choferRefRepository.findById(ID_CHOFER))
                .thenReturn(Optional.of(ChoferRef.builder().id(ID_CHOFER).idUsuario(usuario.id()).build()));
    }

    @Nested
    class Lectura {

        @Test
        void adminSistema_veCualquierViaje() {
            assertDoesNotThrow(() -> autorizacion.verificarLectura(viaje(OTRA_SUCURSAL, null),
                    usuario(Rol.ADMIN_SISTEMA, null)));
            verifyNoInteractions(choferRefRepository);
        }

        @Test
        void personalDeSucursal_veSoloSuSucursal() {
            assertDoesNotThrow(() -> autorizacion.verificarLectura(viaje(SUCURSAL, null),
                    usuario(Rol.CAJERO, SUCURSAL)));
            assertThrows(OperacionNoAutorizadaException.class,
                    () -> autorizacion.verificarLectura(viaje(OTRA_SUCURSAL, null), usuario(Rol.ADMIN_SUCURSAL, SUCURSAL)));
        }

        @Test
        void chofer_veSoloSusViajes() {
            // Arrange
            UsuarioAutenticado chofer = usuario(Rol.CHOFER, SUCURSAL);
            choferEnlazadoA(chofer);

            // Act / Assert
            assertDoesNotThrow(() -> autorizacion.verificarLectura(viaje(OTRA_SUCURSAL, ID_CHOFER), chofer));
        }

        @Test
        void chofer_noVeViajesDeOtroChoferNiSinAsignar() {
            // Arrange
            UsuarioAutenticado chofer = usuario(Rol.CHOFER, SUCURSAL);
            when(choferRefRepository.findById(ID_CHOFER))
                    .thenReturn(Optional.of(ChoferRef.builder().id(ID_CHOFER).idUsuario(UUID.randomUUID()).build()));

            // Act / Assert
            assertThrows(OperacionNoAutorizadaException.class,
                    () -> autorizacion.verificarLectura(viaje(SUCURSAL, ID_CHOFER), chofer));
            assertThrows(OperacionNoAutorizadaException.class,
                    () -> autorizacion.verificarLectura(viaje(SUCURSAL, null), chofer));
        }

        @Test
        void chofer_sinReferenciaLocal_noVeElViaje() {
            when(choferRefRepository.findById(ID_CHOFER)).thenReturn(Optional.empty());
            assertThrows(OperacionNoAutorizadaException.class,
                    () -> autorizacion.verificarLectura(viaje(SUCURSAL, ID_CHOFER), usuario(Rol.CHOFER, SUCURSAL)));
        }
    }

    @Nested
    class Escritura {

        @Test
        void soloElAdminDeLaSucursalDelViaje() {
            assertDoesNotThrow(() -> autorizacion.verificarEscritura(viaje(SUCURSAL, null),
                    usuario(Rol.ADMIN_SUCURSAL, SUCURSAL)));
            assertThrows(OperacionNoAutorizadaException.class, () -> autorizacion.verificarEscritura(
                    viaje(OTRA_SUCURSAL, null), usuario(Rol.ADMIN_SUCURSAL, SUCURSAL)));
            assertThrows(OperacionNoAutorizadaException.class, () -> autorizacion.verificarEscritura(
                    viaje(SUCURSAL, null), usuario(Rol.CAJERO, SUCURSAL)));
        }

        @Test
        void programacion_soloEnRutasDeSuSucursal() {
            Ruta ruta = Ruta.builder().idSucursalOrigen(SUCURSAL).build();
            assertDoesNotThrow(() -> autorizacion.verificarProgramacion(ruta, usuario(Rol.ADMIN_SUCURSAL, SUCURSAL)));
            assertThrows(OperacionNoAutorizadaException.class,
                    () -> autorizacion.verificarProgramacion(ruta, usuario(Rol.ADMIN_SUCURSAL, OTRA_SUCURSAL)));
        }
    }

    @Nested
    class SalidaLlegada {

        @Test
        void adminSucursal_soloEnSuSucursal() {
            assertDoesNotThrow(() -> autorizacion.verificarSalidaLlegada(viaje(SUCURSAL, null),
                    usuario(Rol.ADMIN_SUCURSAL, SUCURSAL)));
            assertThrows(OperacionNoAutorizadaException.class, () -> autorizacion.verificarSalidaLlegada(
                    viaje(OTRA_SUCURSAL, null), usuario(Rol.ADMIN_SUCURSAL, SUCURSAL)));
        }

        @Test
        void chofer_soloEnSusViajes() {
            // Arrange
            UsuarioAutenticado chofer = usuario(Rol.CHOFER, SUCURSAL);
            choferEnlazadoA(chofer);

            // Act / Assert
            assertDoesNotThrow(() -> autorizacion.verificarSalidaLlegada(viaje(SUCURSAL, ID_CHOFER), chofer));
            assertThrows(OperacionNoAutorizadaException.class,
                    () -> autorizacion.verificarSalidaLlegada(viaje(SUCURSAL, null), chofer));
        }
    }
}
