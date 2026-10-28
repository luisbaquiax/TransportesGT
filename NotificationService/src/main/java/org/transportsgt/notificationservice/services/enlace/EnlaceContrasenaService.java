package org.transportsgt.notificationservice.services.enlace;

import org.transportsgt.eventos.payload.identidad.DatosEnlaceContrasena;

import java.util.UUID;

/**
 * Correo de activación o restablecimiento de contraseña. Es la excepción al patrón PENDIENTE: lleva un token
 * secreto, así que se envía en el momento y su cuerpo nunca se guarda.
 */
public interface EnlaceContrasenaService {

    /**
     * Envía el enlace y registra la notificación {@code ENVIADA} con el cuerpo fijo
     * {@code [contenido sensible no almacenado]}.
     *
     * @return {@code false} si ese evento ya se había enviado (no se reenvía)
     * @throws org.transportsgt.notificationservice.correo.EnvioCorreoException si el SMTP falla (transitorio: se
     *                                                                          reintenta el evento)
     */
    boolean enviar(UUID idEvento, DatosEnlaceContrasena datos);
}
