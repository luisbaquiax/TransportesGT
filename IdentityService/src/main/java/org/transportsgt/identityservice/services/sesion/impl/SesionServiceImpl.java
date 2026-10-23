package org.transportsgt.identityservice.services.sesion.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.identityservice.config.security.JwtService;
import org.transportsgt.identityservice.config.security.UsuarioAutenticado;
import org.transportsgt.identityservice.config.security.UsuarioPrincipal;
import org.transportsgt.identityservice.dto.request.LoginRequest;
import org.transportsgt.identityservice.dto.response.LoginResponse;
import org.transportsgt.identityservice.dto.response.UsuarioResponse;
import org.transportsgt.identityservice.dto.response.UsuarioSesionResponse;
import org.transportsgt.identityservice.exception.CredencialesInvalidasException;
import org.transportsgt.identityservice.exception.UsuarioInactivoException;
import org.transportsgt.identityservice.mappers.UsuarioMapper;
import org.transportsgt.identityservice.services.comun.BuscadorIdentidad;
import org.transportsgt.identityservice.services.sesion.SesionService;
import org.transportsgt.identityservice.utils.NormalizadorCorreo;

@Service
@RequiredArgsConstructor
public class SesionServiceImpl implements SesionService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final BuscadorIdentidad buscador;
    private final NormalizadorCorreo correos;
    private final UsuarioMapper usuarioMapper;

    @Override
    public LoginResponse login(LoginRequest request) {
        Authentication autenticacion;
        try {
            autenticacion = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                    correos.normalizar(request.correo()), request.contrasena()));
        } catch (BadCredentialsException e) {
            throw new CredencialesInvalidasException();
        } catch (DisabledException e) {
            // Solo llega aquí con la contraseña correcta (ver SecurityConfig.authenticationManager)
            throw new UsuarioInactivoException();
        }
        UsuarioPrincipal principal = (UsuarioPrincipal) autenticacion.getPrincipal();
        JwtService.TokenEmitido token = jwtService.generarToken(principal);
        UsuarioSesionResponse usuario = new UsuarioSesionResponse(principal.id(), principal.correo(),
                principal.nombreCompleto(), principal.rol(), principal.idSucursal());
        return new LoginResponse(token.token(), token.expiraEn(), usuario);
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponse miPerfil(UsuarioAutenticado autenticado) {
        return usuarioMapper.toResponse(buscador.usuario(autenticado.id()));
    }
}
