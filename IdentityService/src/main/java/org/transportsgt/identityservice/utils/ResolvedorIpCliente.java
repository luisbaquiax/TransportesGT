package org.transportsgt.identityservice.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * IP de quien hace la petición.
 */
@Component
public class ResolvedorIpCliente {

    private static final int LARGO_MAXIMO = 45;   // VARCHAR(45): IPv6

    public String de(HttpServletRequest request) {
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
