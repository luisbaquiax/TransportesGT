package org.transportsgt.reportingservice.services.comun;

import org.springframework.stereotype.Component;

/**
 * Textos derivados que comparten los reportes del sistema y de sucursal.
 */
@Component
public class TextosReporte {

    public static final String SIN_DATO = "—";
    public static final String SUCURSAL_DESCONOCIDA = "Sucursal no sincronizada";

    /** {@code "Quetzaltenango → Huehuetenango"}; un extremo desconocido se muestra como "—". */
    public String trayecto(String origen, String destino) {
        return valor(origen) + " → " + valor(destino);
    }

    /** Nombre de la sucursal, o un aviso si su evento aún no llegó. */
    public String sucursal(String nombre) {
        return nombre == null ? SUCURSAL_DESCONOCIDA : nombre;
    }

    public String valor(String texto) {
        return texto == null || texto.isBlank() ? SIN_DATO : texto;
    }
}
