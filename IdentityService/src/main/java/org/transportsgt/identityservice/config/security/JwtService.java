package org.transportsgt.identityservice.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.transportsgt.identityservice.exception.TokenJwtInvalidoException;

import java.time.Clock;
import java.time.Instant;

/**
 * Emite y valida el JWT de acceso (HS256). En cada petición lo valida el resource server con el mismo JwtDecoder.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final JwtProperties propiedades;
    private final Clock clock;

    public TokenEmitido generarToken(UsuarioPrincipal usuario) {
        Instant ahora = clock.instant();
        Instant expiraEn = ahora.plus(propiedades.expiration());

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(propiedades.issuer())
                .issuedAt(ahora)
                .expiresAt(expiraEn)
                .subject(usuario.id().toString())       // authentication.getName() = idUsuario
                .claim("correo", usuario.correo())
                .claim("nombre", usuario.nombreCompleto())
                .claim("rol", usuario.rol().name());   // sin prefijo, igual que en la BD

        // Solo ADMIN_SUCURSAL, CAJERO y CHOFER tienen sucursal
        if (usuario.idSucursal() != null) {
            claims.claim("idSucursal", usuario.idSucursal().toString());
        }

        // NimbusJwtEncoder usa RS256 por defecto: hay que indicar HS256 explícitamente
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();

        return new TokenEmitido(token, expiraEn);
    }

    /**
     * @throws TokenJwtInvalidoException si la firma no coincide, el token expiró o está mal formado
     */
    public Jwt validar(String token) {
        try {
            return jwtDecoder.decode(token);
        } catch (JwtException e) {
            throw new TokenJwtInvalidoException();
        }
    }

    public record TokenEmitido(String token, Instant expiraEn) {
    }
}
