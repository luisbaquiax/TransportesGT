package org.transportsgt.identityservice.services;

import org.transportsgt.eventos.payload.PropositoContrasena;
import org.transportsgt.identityservice.models.TokenContrasena;
import org.transportsgt.identityservice.models.Usuario;

import java.util.UUID;

/**
 * Enlaces de un solo uso para activar o restablecer la contraseña.
 */
public interface TokenContrasenaService {

    /**
     * Invalida los tokens activos del mismo propósito, guarda el SHA-256 del nuevo
     * y publica EnlaceContrasenaEmitido. Debe llamarse dentro de una transacción.
     */
    void emitir(Usuario usuario, PropositoContrasena proposito, String ip);

    /**
     * @throws org.transportsgt.identityservice.exception.TokenContrasenaInvalidoException si no existe,
     *         ya se usó, fue reemplazado, expiró o el usuario está inactivo
     */
    TokenContrasena buscarVigente(String token);

    /**
     * true si el usuario ya pidió el máximo de restablecimientos en la última hora.
     */
    boolean superaLimiteRestablecimiento(UUID idUsuario);
}
