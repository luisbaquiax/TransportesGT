package org.transportsgt.privaterentalservice.services.tarifa;

import org.transportsgt.privaterentalservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.privaterentalservice.dto.request.TarifaRequest;
import org.transportsgt.privaterentalservice.dto.response.TarifaResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Tarifas del precio estimado. Solo SIS las cambia; se conserva el historial.
 */
public interface TarifaAlquilerService {

    /**
     * La más reciente con vigenteDesde ≤ fecha (hoy en Guatemala si es null).
     */
    TarifaResponse vigente(LocalDate fecha);

    List<TarifaResponse> historial();

    /**
     * Crea la tarifa, o reemplaza la que ya tenga la misma vigenteDesde.
     */
    TarifaResponse registrar(TarifaRequest request, UsuarioAutenticado usuario);
}
