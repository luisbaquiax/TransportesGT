package org.transportsgt.identityservice.services.contrasena;

import org.transportsgt.identityservice.config.security.UsuarioAutenticado;
import org.transportsgt.identityservice.dto.request.CambiarContrasenaRequest;
import org.transportsgt.identityservice.dto.request.OlvideContrasenaRequest;
import org.transportsgt.identityservice.dto.request.RestablecerContrasenaRequest;
import org.transportsgt.identityservice.dto.response.EnlaceContrasenaResponse;

/** Ciclo de la contraseña: cambio, "olvidé mi contraseña", validación del enlace y restablecimiento. */
public interface ContrasenaService {

    /** Publica ContrasenaCambiada(CAMBIO). */
    void cambiarContrasena(CambiarContrasenaRequest request, UsuarioAutenticado autenticado);

    /**
     * No revela si el correo existe: el controlador siempre responde 202. Solo emite el enlace si el usuario
     * existe, está activo y no superó el límite por hora. Publica EnlaceContrasenaEmitido(RESTABLECIMIENTO).
     */
    void olvideContrasena(OlvideContrasenaRequest request, String ip);

    /** @throws org.transportsgt.identityservice.exception.TokenContrasenaInvalidoException (410) */
    EnlaceContrasenaResponse validarEnlace(String token);

    /** Para RESTABLECIMIENTO y ACTIVACION. Publica ContrasenaCambiada con el motivo según el propósito. */
    void restablecerContrasena(RestablecerContrasenaRequest request);
}
