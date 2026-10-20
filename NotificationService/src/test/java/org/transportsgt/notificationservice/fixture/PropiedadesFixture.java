package org.transportsgt.notificationservice.fixture;

import org.transportsgt.notificationservice.config.NotificacionesProperties;
import org.transportsgt.notificationservice.utils.Fechas;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;

public final class PropiedadesFixture {

    private PropiedadesFixture() {
    }

    /** Como application.properties, con la barra final en FRONTEND_URL para probar que no se duplica. */
    public static NotificacionesProperties propiedades() {
        return new NotificacionesProperties("http://localhost:5173/", "/restablecer-contrasena", "/activar-cuenta",
                "Transportes GT <no-responder@transportes.gt>",
                new NotificacionesProperties.Envio(20, 5, Duration.ofMinutes(1)));
    }

    public static Fechas fechas() {
        return new Fechas(Clock.fixed(EventoFixture.AHORA, ZoneOffset.UTC));
    }
}
