package org.transportsgt.identityservice.dto.request;

/**
 * Política mínima: 8 caracteres o más, con mayúscula, minúscula y número.
 * Máximo 72 caracteres porque BCrypt ignora lo que sigue.
 */
public final class PoliticaContrasena {

    public static final String REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,72}$";
    public static final String MENSAJE =
            "debe tener entre 8 y 72 caracteres e incluir al menos una mayúscula, una minúscula y un número";

    private PoliticaContrasena() {
    }
}
