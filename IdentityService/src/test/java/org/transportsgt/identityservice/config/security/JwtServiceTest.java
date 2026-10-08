package org.transportsgt.identityservice.config.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.identityservice.exception.TokenJwtInvalidoException;
import org.transportsgt.identityservice.fixtures.SucursalFixture;
import org.transportsgt.identityservice.fixtures.UsuarioFixture;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private static final String SECRETO = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final String OTRO_SECRETO = Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());
    private static final JwtProperties PROPIEDADES = new JwtProperties(SECRETO, Duration.ofMinutes(60), "servicio-identidad");
    private static final UUID ID_USUARIO = UUID.randomUUID();
    private static final UUID ID_SUCURSAL = UUID.randomUUID();

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = crear(PROPIEDADES, Clock.systemUTC());
    }

    private static JwtService crear(JwtProperties propiedades, Clock clock) {
        JwtConfig config = new JwtConfig(propiedades);
        return new JwtService(config.jwtEncoder(), config.jwtDecoder(), propiedades, clock);
    }

    @Test
    void generarYValidar_incluyeLosClaimsDelUsuario() {
        // Arrange
        UsuarioPrincipal cajero = UsuarioPrincipal.from(UsuarioFixture.cajero(ID_USUARIO, SucursalFixture.activa(ID_SUCURSAL)));

        // Act
        JwtService.TokenEmitido emitido = jwtService.generarToken(cajero);
        Jwt jwt = jwtService.validar(emitido.token());

        // Assert
        UsuarioAutenticado autenticado = UsuarioAutenticado.de(jwt);
        assertAll(
                () -> assertEquals(ID_USUARIO.toString(), jwt.getSubject()),
                () -> assertEquals("servicio-identidad", jwt.getClaimAsString("iss")),
                () -> assertEquals("CAJERO", jwt.getClaimAsString("rol")),
                () -> assertEquals(ID_SUCURSAL, autenticado.idSucursal()),
                () -> assertEquals(Rol.CAJERO, autenticado.rol()),
                () -> assertEquals(cajero.correo(), autenticado.correo()),
                () -> assertEquals(cajero.nombreCompleto(), autenticado.nombre()),
                () -> assertEquals(emitido.expiraEn().getEpochSecond(), jwt.getExpiresAt().getEpochSecond())
        );
    }

    @Test
    void generar_adminSistemaSinSucursal() {
        // Arrange
        UsuarioPrincipal admin = UsuarioPrincipal.from(UsuarioFixture.adminSistema(ID_USUARIO));

        // Act
        Jwt jwt = jwtService.validar(jwtService.generarToken(admin).token());

        // Assert
        assertAll(
                () -> assertNull(jwt.getClaimAsString("idSucursal")),
                () -> assertNull(UsuarioAutenticado.de(jwt).idSucursal())
        );
    }

    @Test
    void validar_cuandoFirmaDeOtroSecreto_lanzaTokenInvalido() {
        // Arrange
        String ajeno = crear(new JwtProperties(OTRO_SECRETO, Duration.ofMinutes(60), "x"), Clock.systemUTC())
                .generarToken(UsuarioPrincipal.from(UsuarioFixture.adminSistema(ID_USUARIO))).token();

        // Assert
        assertThrows(TokenJwtInvalidoException.class, () -> jwtService.validar(ajeno));
    }

    @Test
    void validar_cuandoExpirado_lanzaTokenInvalido() {
        // Arrange
        Clock haceDosHoras = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        String vencido = crear(PROPIEDADES, haceDosHoras)
                .generarToken(UsuarioPrincipal.from(UsuarioFixture.adminSistema(ID_USUARIO))).token();

        // Assert
        assertThrows(TokenJwtInvalidoException.class, () -> jwtService.validar(vencido));
    }

    @Test
    void validar_cuandoMalFormado_lanzaTokenInvalido() {
        assertThrows(TokenJwtInvalidoException.class, () -> jwtService.validar("no.es.jwt"));
    }
}
