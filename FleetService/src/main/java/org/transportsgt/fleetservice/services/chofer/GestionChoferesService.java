package org.transportsgt.fleetservice.services.chofer;

import org.transportsgt.fleetservice.config.security.UsuarioAutenticado;
import org.transportsgt.fleetservice.dto.request.ActualizarChoferRequest;
import org.transportsgt.fleetservice.dto.request.CambiarActivoRequest;
import org.transportsgt.fleetservice.dto.request.CrearChoferRequest;
import org.transportsgt.fleetservice.dto.response.ChoferResponse;

import java.util.UUID;

/**
 * Cambios sobre los choferes (solo ADMIN_SUCURSAL de la sucursal del chofer). Cada cambio publica un evento.
 */
public interface GestionChoferesService {

    /**
     * Crea el chofer en la sucursal del administrador. Publica ChoferRegistrado.
     */
    ChoferResponse crear(CrearChoferRequest request, UsuarioAutenticado autenticado);

    /**
     * Actualiza los datos editables. Publica ChoferActualizado
     * (Notificaciones reprograma los avisos de licencia).
     */
    ChoferResponse actualizar(UUID id, ActualizarChoferRequest request, UsuarioAutenticado autenticado);

    /**
     * Activa o desactiva. Publica ChoferActualizado si cambia.
     */
    ChoferResponse cambiarActivo(UUID id, CambiarActivoRequest request, UsuarioAutenticado autenticado);
}
