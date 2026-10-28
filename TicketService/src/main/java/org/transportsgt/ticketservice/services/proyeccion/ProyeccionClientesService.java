package org.transportsgt.ticketservice.services.proyeccion;

import org.transportsgt.eventos.payload.cartera.DatosPerfilClienteActualizado;

/**
 * Mantiene {@code cliente_ref}: NIT y nombre de los compradores en línea.
 */
public interface ProyeccionClientesService {

    /**
     * Aplica PerfilClienteActualizado: crea o actualiza el perfil solo si la versión es mayor que la guardada.
     *
     * @return {@code true} si cambió {@code cliente_ref}
     */
    boolean aplicar(DatosPerfilClienteActualizado datos, Long versionAgregado);
}
