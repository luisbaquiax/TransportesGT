package org.transportsgt.tripservice.services.viaje;

import org.transportsgt.tripservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.tripservice.dto.request.Requests;
import org.transportsgt.tripservice.dto.response.Responses;

import java.util.UUID;

/** Registros inmutables de salida y llegada (administrador de la sucursal o el chofer del viaje). */
public interface RegistroViajeService {

    /** PROGRAMADO → EN_TRANSITO. Publica SalidaRegistrada y ViajeActualizado. */
    Responses.ViajeResponse registrarSalida(UUID id, Requests.SalidaRequest request, UsuarioAutenticado usuario);

    /** EN_TRANSITO → FINALIZADO. Publica LlegadaRegistrada y ViajeActualizado. */
    Responses.ViajeResponse registrarLlegada(UUID id, Requests.LlegadaRequest request, UsuarioAutenticado usuario);
}
