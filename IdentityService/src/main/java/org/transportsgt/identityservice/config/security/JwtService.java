package org.transportsgt.identityservice.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Emite el JWT de acceso. La validación la hace el resource server (JwtDecoder).
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtEncoder jwtEncoder;

    @Value("${jwt.expiration}")
    private Duration expiracion;

    @Value("${jwt.issuer}")
    private String emisor;

    public TokenEmitido generarToken(UsuarioPrincipal usuario) {
        Instant ahora = Instant.now();
        Instant expiraEn = ahora.plus(expiracion);

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(emisor)
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

    public record TokenEmitido(String token, Instant expiraEn) {
    }
}