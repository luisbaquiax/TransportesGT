package org.transportsgt.notificationservice.services.bandeja;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

/**
 * Claves de {@code notificacion.clave_dedup}: un correo por evento (o aviso) y destinatario. El correo va en
 * minúsculas para que {@code Ana@x.com} y {@code ana@x.com} sean el mismo destinatario.
 */
@Component
public class ClaveDedup {

    public String evento(UUID idEvento, String correo) {
        return "evt:" + idEvento + ":" + correo.toLowerCase(Locale.ROOT);
    }

    public String licencia(UUID idRecordatorio, String correo) {
        return "lic:" + idRecordatorio + ":" + correo.toLowerCase(Locale.ROOT);
    }
}
