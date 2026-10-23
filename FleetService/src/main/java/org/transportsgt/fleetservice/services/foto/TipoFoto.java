package org.transportsgt.fleetservice.services.foto;

import java.util.Locale;

/**
 * Carpeta de la foto dentro del almacenamiento: {@code buses/...} o {@code choferes/...}.
 */
public enum TipoFoto {
    BUSES, CHOFERES;

    public String carpeta() {
        return name().toLowerCase(Locale.ROOT);
    }
}
