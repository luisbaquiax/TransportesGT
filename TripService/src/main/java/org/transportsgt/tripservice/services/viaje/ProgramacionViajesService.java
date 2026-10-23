package org.transportsgt.tripservice.services.viaje;

import org.transportsgt.tripservice.config.security.UsuarioAutenticado;
import org.transportsgt.tripservice.dto.request.Requests;
import org.transportsgt.tripservice.dto.response.Responses;

import java.util.UUID;

/** Programación de viajes por el administrador de sucursal (los de alquiler nacen con AlquilerConfirmado). */
public interface ProgramacionViajesService {

    /** Viaje REGULAR en una ruta de la sucursal del administrador. Publica ViajeProgramado. */
    Responses.ViajeResponse crearRegular(Requests.ViajeRegularRequest request, UsuarioAutenticado usuario);

    /**
     * Cambia bus, chofer o fechas (no el tipo). Si cambia el bus de un viaje regular, consulta a Boletos el asiento
     * máximo vendido (falla cerrada). Publica ViajeActualizado.
     */
    Responses.ViajeResponse actualizar(UUID id, Requests.ViajeUpdateRequest request, UsuarioAutenticado usuario);

    /** Bus y chofer de un alquiler PENDIENTE_ASIGNACION, que pasa a PROGRAMADO. Publica ViajeActualizado. */
    Responses.ViajeResponse asignar(UUID id, Requests.AsignacionRequest request, UsuarioAutenticado usuario);

    /** Solo si no ha iniciado ni finalizado. Publica ViajeEliminado. */
    void eliminar(UUID id, UsuarioAutenticado usuario);
}
