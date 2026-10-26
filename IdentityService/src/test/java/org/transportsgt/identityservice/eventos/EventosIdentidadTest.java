package org.transportsgt.identityservice.eventos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.RegistroOutbox;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosContrasenaCambiada;
import org.transportsgt.eventos.payload.DatosEnlaceContrasena;
import org.transportsgt.eventos.payload.DatosSucursal;
import org.transportsgt.eventos.payload.DatosUsuario;
import org.transportsgt.eventos.payload.MotivoContrasena;
import org.transportsgt.eventos.payload.PropositoContrasena;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.identityservice.fixtures.SucursalFixture;
import org.transportsgt.identityservice.fixtures.UsuarioFixture;
import org.transportsgt.identityservice.mappers.SucursalMapper;
import org.transportsgt.identityservice.mappers.UsuarioMapper;
import org.transportsgt.identityservice.models.Sucursal;
import org.transportsgt.identityservice.models.Usuario;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventosIdentidadTest {

    private static final UUID ID_SUCURSAL = UUID.randomUUID();
    private static final UUID ID_USUARIO = UUID.randomUUID();
    private static final Instant INSTANTE = Instant.parse("2026-10-07T16:00:00Z");
    private static final String TOKEN = "tokenEnClaro";

    @Mock
    private RegistroOutbox outbox;
    @Spy
    private SucursalMapper sucursalMapper = Mappers.getMapper(SucursalMapper.class);
    @Spy
    private UsuarioMapper usuarioMapper = Mappers.getMapper(UsuarioMapper.class);
    @InjectMocks
    private EventosIdentidad eventos;

    private Sucursal sucursal;
    private Usuario cajero;

    @BeforeEach
    void setUp() {
        sucursal = SucursalFixture.activa(ID_SUCURSAL);
        sucursal.setVersion(4L);
        cajero = UsuarioFixture.cajero(ID_USUARIO, sucursal);
        cajero.setVersion(2L);
    }

    @Test
    void sucursalCreada_registraPayloadConLaVersionDeLaEntidad() {
        // Arrange
        ArgumentCaptor<Object> datos = ArgumentCaptor.forClass(Object.class);

        // Act
        eventos.sucursalCreada(sucursal);

        // Assert
        verify(outbox).registrar(eq(TipoEvento.SUCURSAL_CREADA), eq(ID_SUCURSAL), eq(4L), datos.capture());
        DatosSucursal payload = (DatosSucursal) datos.getValue();
        assertAll(
                () -> assertEquals(ID_SUCURSAL, payload.idSucursal()),
                () -> assertEquals(SucursalFixture.NOMBRE, payload.nombre()),
                () -> assertEquals(sucursal.getLatitud(), payload.latitud()),
                () -> assertTrue(payload.activa())
        );
    }

    @Test
    void sucursalActualizada_registraConElTipoCorrecto() {
        // Arrange
        sucursal.setActiva(false);
        ArgumentCaptor<Object> datos = ArgumentCaptor.forClass(Object.class);

        // Act
        eventos.sucursalActualizada(sucursal);

        // Assert
        verify(outbox).registrar(eq(TipoEvento.SUCURSAL_ACTUALIZADA), eq(ID_SUCURSAL), eq(4L), datos.capture());
        assertFalse(((DatosSucursal) datos.getValue()).activa());
    }

    @Test
    void usuarioCreado_incluyeRolYSucursal() {
        // Arrange
        ArgumentCaptor<Object> datos = ArgumentCaptor.forClass(Object.class);

        // Act
        eventos.usuarioCreado(cajero);

        // Assert
        verify(outbox).registrar(eq(TipoEvento.USUARIO_CREADO), eq(ID_USUARIO), eq(2L), datos.capture());
        DatosUsuario payload = (DatosUsuario) datos.getValue();
        assertAll(
                () -> assertEquals(ID_USUARIO, payload.idUsuario()),
                () -> assertEquals(Rol.CAJERO, payload.rol()),
                () -> assertEquals(ID_SUCURSAL, payload.idSucursal()),
                () -> assertEquals(cajero.getCorreo(), payload.correo()),
                () -> assertTrue(payload.activo())
        );
    }

    @Test
    void usuarioActualizado_clienteSinSucursal() {
        // Arrange
        Usuario cliente = UsuarioFixture.cliente(ID_USUARIO);
        cliente.setActivo(false);
        ArgumentCaptor<Object> datos = ArgumentCaptor.forClass(Object.class);

        // Act
        eventos.usuarioActualizado(cliente);

        // Assert
        verify(outbox).registrar(eq(TipoEvento.USUARIO_ACTUALIZADO), eq(ID_USUARIO), eq(0L), datos.capture());
        DatosUsuario payload = (DatosUsuario) datos.getValue();
        assertAll(
                () -> assertEquals(null, payload.idSucursal()),
                () -> assertFalse(payload.activo())
        );
    }

    @Test
    void enlaceContrasenaEmitido_llevaTokenYExpiracion() {
        // Arrange
        ArgumentCaptor<Object> datos = ArgumentCaptor.forClass(Object.class);

        // Act
        eventos.enlaceContrasenaEmitido(cajero, PropositoContrasena.ACTIVACION, TOKEN, INSTANTE);

        // Assert
        verify(outbox).registrar(eq(TipoEvento.ENLACE_CONTRASENA_EMITIDO), eq(ID_USUARIO), eq(2L), datos.capture());
        DatosEnlaceContrasena payload = (DatosEnlaceContrasena) datos.getValue();
        assertAll(
                () -> assertEquals(TOKEN, payload.token()),
                () -> assertEquals(PropositoContrasena.ACTIVACION, payload.proposito()),
                () -> assertEquals(INSTANTE, payload.expiraEn()),
                () -> assertFalse(payload.toString().contains(TOKEN))
        );
    }

    @Test
    void contrasenaCambiada_llevaMotivoYFecha() {
        // Arrange
        ArgumentCaptor<Object> datos = ArgumentCaptor.forClass(Object.class);

        // Act
        eventos.contrasenaCambiada(cajero, MotivoContrasena.RESTABLECIMIENTO, INSTANTE);

        // Assert
        verify(outbox).registrar(eq(TipoEvento.CONTRASENA_CAMBIADA), eq(ID_USUARIO), eq(2L), datos.capture());
        DatosContrasenaCambiada payload = (DatosContrasenaCambiada) datos.getValue();
        assertAll(
                () -> assertEquals(MotivoContrasena.RESTABLECIMIENTO, payload.motivo()),
                () -> assertEquals(INSTANTE, payload.cambiadaEn()),
                () -> assertEquals(cajero.getNombreCompleto(), payload.nombreCompleto())
        );
    }
}
