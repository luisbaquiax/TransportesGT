package org.transportsgt.identityservice.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP de quien hace la petición (auditoría de token_contrasena.ip_solicitud).
 * Detrás del gateway llega en X-Forwarded-For; se toma la primera (la del cliente).
 */
public final class IpCliente {

    private static final int LARGO_MAXIMO = 45;   // VARCHAR(45): IPv6

    private IpCliente() {
    }

    public static String de(HttpServletRequest request) {
        String reenviada = request.getHeader("X-Forwarded-For");
        String ip = reenviada != null && !reenviada.isBlank()
                ? reenviada.split(",")[0].trim()
                : request.getRemoteAddr();
        if (ip == null) {
            return null;
        }
        return ip.length() > LARGO_MAXIMO ? ip.substring(0, LARGO_MAXIMO) : ip;
    }
}
