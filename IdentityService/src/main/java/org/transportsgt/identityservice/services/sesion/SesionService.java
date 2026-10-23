package org.transportsgt.identityservice.services.sesion;

import org.transportsgt.identityservice.config.security.UsuarioAutenticado;
import org.transportsgt.identityservice.dto.request.LoginRequest;
import org.transportsgt.identityservice.dto.response.LoginResponse;
import org.transportsgt.identityservice.dto.response.UsuarioResponse;

/** Inicio de sesión (emite el JWT) y datos del usuario del token. */
public interface SesionService {

    /**
     * @throws org.transportsgt.identityservice.exception.CredencialesInvalidasException (401)
     * @throws org.transportsgt.identityservice.exception.UsuarioInactivoException (403), solo con la contraseña correcta
     */
    LoginResponse login(LoginRequest request);

    UsuarioResponse miPerfil(UsuarioAutenticado autenticado);
}
