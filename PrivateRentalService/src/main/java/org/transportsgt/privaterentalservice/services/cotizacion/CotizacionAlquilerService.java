package org.transportsgt.privaterentalservice.services.cotizacion;

import org.transportsgt.privaterentalservice.config.security.UsuarioAutenticado;
import org.transportsgt.privaterentalservice.dto.request.CotizacionRequest;
import org.transportsgt.privaterentalservice.dto.response.AlquilerResponse;

import java.util.UUID;

public interface CotizacionAlquilerService {

    /** SUC de la sucursal del alquiler fija precio final y llegada: SOLICITADO o COTIZADO → COTIZADO. */
    AlquilerResponse cotizar(UUID id, CotizacionRequest request, UsuarioAutenticado usuario);
}
