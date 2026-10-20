package org.transportsgt.notificationservice.services.bandeja;

import java.util.UUID;

/**
 * A quién va un correo.
 *
 * @param idUsuario nulo si el destinatario no tiene cuenta (p. ej. un chofer sin acceso al sistema)
 */
public record Destinatario(UUID idUsuario, String correo, String nombre) {
}
