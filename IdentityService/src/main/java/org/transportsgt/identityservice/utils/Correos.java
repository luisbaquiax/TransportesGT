package org.transportsgt.identityservice.utils;

import java.util.Locale;

/**
 * Normalización y enmascarado de correos.
 */
public final class Correos {

    private Correos() {
    }

    /**
     * Sin espacios y en minúsculas: así se guarda y se busca siempre.
     */
    public static String normalizar(String correo) {
        return correo == null ? null : correo.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * {@code ana.lopez@transportes.gt} → {@code a***z@transportes.gt}.
     */
    public static String enmascarar(String correo) {
        if (correo == null) {
            return null;
        }
        int arroba = correo.indexOf('@');
        if (arroba <= 0) {
            return "***";
        }
        String local = correo.substring(0, arroba);
        String visible = local.length() <= 2
                ? local.charAt(0) + "***"
                : local.charAt(0) + "***" + local.charAt(local.length() - 1);
        return visible + correo.substring(arroba);
    }
}
