package org.transportsgt.tripservice.services.ruta;

import org.transportsgt.tripservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.tripservice.dto.request.Requests;
import org.transportsgt.tripservice.dto.response.Responses;

import java.util.List;
import java.util.UUID;

/**
 * Rutas de sucursal a sucursal. Las gestiona solo el administrador de la sucursal de origen
 * y se eliminan solo si no tienen viajes asociados.
 */
public interface RutaService {

    /** Filtros opcionales por origen y destino. */
    List<Responses.RutaResponse> listar(UUID idSucursalOrigen, UUID idSucursalDestino);

    Responses.RutaResponse detalle(UUID id);

    /** Publica RutaCreada. */
    Responses.RutaResponse crear(Requests.RutaRequest request, UsuarioAutenticado usuario);

    /** No cambia la sucursal de origen. Publica RutaActualizada. */
    Responses.RutaResponse actualizar(UUID id, Requests.RutaRequest request, UsuarioAutenticado usuario);

    /** 409 RUTA_CON_VIAJES si tiene viajes. Publica RutaEliminada. */
    void eliminar(UUID id, UsuarioAutenticado usuario);
}
