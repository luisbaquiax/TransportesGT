package org.transportsgt.identityservice.services.contrasena.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.identidad.PropositoContrasena;
import org.transportsgt.identityservice.config.IdentidadProperties;
import org.transportsgt.identityservice.eventos.EventosIdentidad;
import org.transportsgt.identityservice.exception.TokenContrasenaInvalidoException;
import org.transportsgt.identityservice.models.TokenContrasena;
import org.transportsgt.identityservice.models.Usuario;
import org.transportsgt.identityservice.repositories.TokenContrasenaRepository;
import org.transportsgt.identityservice.services.contrasena.EnlacesContrasenaService;
import org.transportsgt.identityservice.utils.GeneradorTokenSeguro;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnlacesContrasenaServiceImpl implements EnlacesContrasenaService {

    private final TokenContrasenaRepository tokenRepository;
    private final GeneradorTokenSeguro generadorToken;
    private final EventosIdentidad eventos;
    private final IdentidadProperties propiedades;
    private final Clock clock;

    @Override
    @Transactional
    public void emitir(Usuario usuario, PropositoContrasena proposito, String ip) {
        OffsetDateTime ahora = OffsetDateTime.now(clock);
        // Libera uq_token_activo: solo puede haber un token activo por usuario y propósito
        tokenRepository.invalidarActivos(usuario.getId(), proposito, ahora);

        String token = generadorToken.generar();
        OffsetDateTime expiraEn = ahora.plus(vigencia(proposito));
        TokenContrasena registro = TokenContrasena.builder()
                .usuario(usuario)
                .hashToken(generadorToken.sha256Hex(token))   // el token en claro no se guarda
                .proposito(proposito)
                .expiraEn(expiraEn)
                .ipSolicitud(ip)
                .build();
        tokenRepository.save(registro);

        eventos.enlaceContrasenaEmitido(usuario, proposito, token, expiraEn.toInstant());
    }

    @Override
    @Transactional(readOnly = true)
    public TokenContrasena buscarVigente(String token) {
        if (token == null || token.isBlank()) {
            throw new TokenContrasenaInvalidoException();
        }
        OffsetDateTime ahora = OffsetDateTime.now(clock);
        return tokenRepository.findByHashToken(generadorToken.sha256Hex(token.trim()))
                .filter(t -> t.estaVigente(ahora))
                .filter(t -> Boolean.TRUE.equals(t.getUsuario().getActivo()))
                .orElseThrow(TokenContrasenaInvalidoException::new);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean superaLimiteRestablecimiento(UUID idUsuario) {
        OffsetDateTime haceUnaHora = OffsetDateTime.now(clock).minusHours(1);
        long solicitudes = tokenRepository.countByUsuarioIdAndPropositoAndCreadoEnAfter(
                idUsuario, PropositoContrasena.RESTABLECIMIENTO, haceUnaHora);
        return solicitudes >= propiedades.maxSolicitudesPorHora();
    }

    @Override
    @Transactional
    public int limpiarVencidos() {
        OffsetDateTime limite = OffsetDateTime.now(clock).minus(propiedades.retencionTokens());
        int borrados = tokenRepository.eliminarVencidosAntesDe(limite);
        if (borrados > 0) {
            log.info("Limpieza de tokens: {} token(s) vencidos antes de {} eliminados", borrados, limite);
        }
        return borrados;
    }

    private Duration vigencia(PropositoContrasena proposito) {
        return proposito == PropositoContrasena.ACTIVACION
                ? propiedades.vigenciaActivacion()
                : propiedades.vigenciaRestablecimiento();
    }
}
