package org.transportsgt.notificationservice.services.proyeccion;

import org.transportsgt.eventos.payload.DatosUsuario;

/**
 * Copia local de los usuarios (tabla {@code contacto}), alimentada por UsuarioCreado / UsuarioActualizado.
 */
public interface ProyeccionContactosService {

    /**
     * Upsert por versión: se ignora si {@code versionAgregado} no es mayor que la guardada.
     *
     * @return {@code true} si se aplicó
     */
    boolean aplicar(DatosUsuario datos, Long versionAgregado);
}
