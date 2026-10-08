package org.transportsgt.identityservice.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.payload.PropositoContrasena;
import org.transportsgt.identityservice.config.IdentidadProperties;
import org.transportsgt.identityservice.eventos.EventosIdentidad;
import org.transportsgt.identityservice.exception.TokenContrasenaInvalidoException;
import org.transportsgt.identityservice.fixtures.UsuarioFixture;
import org.transportsgt.identityservice.models.TokenContrasena;
import org.transportsgt.identityservice.models.Usuario;
import org.transportsgt.identityservice.repositories.TokenContrasenaRepository;
import org.transportsgt.identityservice.utils.GeneradorTokenSeguro;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenContrasenaServiceImplTest {

    private static final Instant AHORA = Instant.parse("2026-10-07T16:00:00Z");
    private static final OffsetDateTime AHORA_ODT = OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC);
    private static final UUID ID_USUARIO = UUID.randomUUID();
    private static final String TOKEN = "q3xY0k1v9Q8hZ2bN4mR7sT6uW5pL0aE3cD1fG8jK2nM";
    private static final String HASH = "b".repeat(64);
    private static final String IP = "10.0.0.1";
    private static final IdentidadProperties PROPIEDADES = new IdentidadProperties(
            Duration.ofMinutes(30), Duration.ofHours(48), 3, Duration.ofDays(7));

    @Mock
    private TokenContrasenaRepository tokenRepository;
    @Mock
    private GeneradorTokenSeguro generadorToken;
    @Mock
    private EventosIdentidad eventos;

    private TokenContrasenaServiceImpl tokenService;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        tokenService = new TokenContrasenaServiceImpl(tokenRepository, generadorToken, eventos, PROPIEDADES,
                Clock.fixed(AHORA, ZoneOffset.UTC));
        usuario = UsuarioFixture.cliente(ID_USUARIO);
    }

    @Test
    void emitir_restablecimiento_invalidaAnterioresGuardaHashYPublicaEnlace() {
        // Arrange
        ArgumentCaptor<TokenContrasena> captor = ArgumentCaptor.forClass(TokenContrasena.class);
        when(generadorToken.generar()).thenReturn(TOKEN);
        when(generadorToken.sha256Hex(TOKEN)).thenReturn(HASH);

        // Act
        tokenService.emitir(usuario, PropositoContrasena.RESTABLECIMIENTO, IP);

        // Assert
        InOrder orden = inOrder(tokenRepository, eventos);
        assertAll(
                () -> orden.verify(tokenRepository).invalidarActivos(ID_USUARIO, PropositoContrasena.RESTABLECIMIENTO, AHORA_ODT),
                () -> orden.verify(tokenRepository).save(captor.capture()),
                () -> orden.verify(eventos).enlaceContrasenaEmitido(usuario, PropositoContrasena.RESTABLECIMIENTO,
                        TOKEN, AHORA.plus(Duration.ofMinutes(30))),
                () -> assertEquals(HASH, captor.getValue().getHashToken()),
                () -> assertEquals(AHORA_ODT.plusMinutes(30), captor.getValue().getExpiraEn()),
                () -> assertEquals(IP, captor.getValue().getIpSolicitud()),
                () -> assertSame(usuario, captor.getValue().getUsuario())
        );
    }

    @Test
    void emitir_activacion_usaVigenciaDe48Horas() {
        // Arrange
        ArgumentCaptor<TokenContrasena> captor = ArgumentCaptor.forClass(TokenContrasena.class);
        when(generadorToken.generar()).thenReturn(TOKEN);
        when(generadorToken.sha256Hex(TOKEN)).thenReturn(HASH);

        // Act
        tokenService.emitir(usuario, PropositoContrasena.ACTIVACION, null);

        // Assert
        assertAll(
                () -> verify(tokenRepository).save(captor.capture()),
                () -> assertEquals(AHORA_ODT.plusHours(48), captor.getValue().getExpiraEn()),
                () -> assertEquals(PropositoContrasena.ACTIVACION, captor.getValue().getProposito())
        );
    }

    @Test
    void buscarVigente_devuelveElToken() {
        // Arrange
        TokenContrasena token = token(AHORA_ODT.plusMinutes(10));
        when(generadorToken.sha256Hex(TOKEN)).thenReturn(HASH);
        when(tokenRepository.findByHashToken(HASH)).thenReturn(Optional.of(token));

        // Act
        TokenContrasena result = tokenService.buscarVigente(" " + TOKEN + " ");

        // Assert
        assertSame(token, result);
    }

    @Test
    void buscarVigente_cuandoNoExiste_lanzaGone() {
        // Arrange
        when(generadorToken.sha256Hex(TOKEN)).thenReturn(HASH);
        when(tokenRepository.findByHashToken(HASH)).thenReturn(Optional.empty());

        // Assert
        assertThrows(TokenContrasenaInvalidoException.class, () -> tokenService.buscarVigente(TOKEN));
    }

    @Test
    void buscarVigente_cuandoVencido_lanzaGone() {
        // Arrange
        when(generadorToken.sha256Hex(TOKEN)).thenReturn(HASH);
        when(tokenRepository.findByHashToken(HASH)).thenReturn(Optional.of(token(AHORA_ODT.minusSeconds(1))));

        // Assert
        assertThrows(TokenContrasenaInvalidoException.class, () -> tokenService.buscarVigente(TOKEN));
    }

    @Test
    void buscarVigente_cuandoYaUsado_lanzaGone() {
        // Arrange
        TokenContrasena token = token(AHORA_ODT.plusMinutes(10));
        token.setUsadoEn(AHORA_ODT.minusMinutes(1));
        when(generadorToken.sha256Hex(TOKEN)).thenReturn(HASH);
        when(tokenRepository.findByHashToken(HASH)).thenReturn(Optional.of(token));

        // Assert
        assertThrows(TokenContrasenaInvalidoException.class, () -> tokenService.buscarVigente(TOKEN));
    }

    @Test
    void buscarVigente_cuandoUsuarioInactivo_lanzaGone() {
        // Arrange
        usuario.setActivo(false);
        when(generadorToken.sha256Hex(TOKEN)).thenReturn(HASH);
        when(tokenRepository.findByHashToken(HASH)).thenReturn(Optional.of(token(AHORA_ODT.plusMinutes(10))));

        // Assert
        assertThrows(TokenContrasenaInvalidoException.class, () -> tokenService.buscarVigente(TOKEN));
    }

    @Test
    void buscarVigente_cuandoTokenVacio_lanzaGoneSinConsultar() {
        assertAll(
                () -> assertThrows(TokenContrasenaInvalidoException.class, () -> tokenService.buscarVigente(null)),
                () -> assertThrows(TokenContrasenaInvalidoException.class, () -> tokenService.buscarVigente("  ")),
                () -> verifyNoInteractions(tokenRepository)
        );
    }

    @Test
    void superaLimiteRestablecimiento_trueConTresSolicitudesEnLaUltimaHora() {
        // Arrange
        when(tokenRepository.countByUsuarioIdAndPropositoAndCreadoEnAfter(ID_USUARIO,
                PropositoContrasena.RESTABLECIMIENTO, AHORA_ODT.minusHours(1))).thenReturn(3L);

        // Assert
        assertTrue(tokenService.superaLimiteRestablecimiento(ID_USUARIO));
    }

    @Test
    void superaLimiteRestablecimiento_falseConMenosDeTres() {
        // Arrange
        when(tokenRepository.countByUsuarioIdAndPropositoAndCreadoEnAfter(any(), any(), any())).thenReturn(2L);

        // Assert
        assertFalse(tokenService.superaLimiteRestablecimiento(ID_USUARIO));
    }

    private TokenContrasena token(OffsetDateTime expiraEn) {
        return TokenContrasena.builder()
                .id(UUID.randomUUID())
                .usuario(usuario)
                .hashToken(HASH)
                .proposito(PropositoContrasena.RESTABLECIMIENTO)
                .expiraEn(expiraEn)
                .build();
    }
}
