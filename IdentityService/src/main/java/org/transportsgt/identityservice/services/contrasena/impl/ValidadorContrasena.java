package org.transportsgt.identityservice.services.contrasena.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.payload.MotivoContrasena;
import org.transportsgt.eventos.payload.PropositoContrasena;
import org.transportsgt.identityservice.exception.ContrasenaActualIncorrectaException;
import org.transportsgt.identityservice.exception.ContrasenaRepetidaException;
import org.transportsgt.identityservice.models.Usuario;

/**
 * Reglas de la contraseña nueva y el motivo que viaja en ContrasenaCambiada.
 */
@Component
@RequiredArgsConstructor
class ValidadorContrasena {

    private final PasswordEncoder passwordEncoder;

    void verificarActual(Usuario usuario, String contrasenaActual) {
        if (!passwordEncoder.matches(contrasenaActual, usuario.getHashContrasena())) {
            throw new ContrasenaActualIncorrectaException();
        }
    }

    /**
     * La nueva no puede ser igual a la actual.
     */
    void verificarDistinta(Usuario usuario, String nuevaContrasena) {
        if (passwordEncoder.matches(nuevaContrasena, usuario.getHashContrasena())) {
            throw new ContrasenaRepetidaException();
        }
    }

    MotivoContrasena motivoDe(PropositoContrasena proposito) {
        return proposito == PropositoContrasena.ACTIVACION
                ? MotivoContrasena.ACTIVACION
                : MotivoContrasena.RESTABLECIMIENTO;
    }
}
