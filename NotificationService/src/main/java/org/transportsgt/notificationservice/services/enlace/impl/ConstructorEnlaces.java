package org.transportsgt.notificationservice.services.enlace.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.payload.PropositoContrasena;
import org.transportsgt.notificationservice.config.NotificacionesProperties;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
class ConstructorEnlaces {

    private final NotificacionesProperties propiedades;

    String construir(PropositoContrasena proposito, String token) {
        String base = propiedades.frontendUrl().endsWith("/")
                ? propiedades.frontendUrl().substring(0, propiedades.frontendUrl().length() - 1)
                : propiedades.frontendUrl();
        String ruta = proposito == PropositoContrasena.ACTIVACION ? propiedades.rutaActivar() : propiedades.rutaRestablecer();
        return base + ruta + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
