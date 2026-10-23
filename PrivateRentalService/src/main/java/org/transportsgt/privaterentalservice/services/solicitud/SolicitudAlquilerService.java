package org.transportsgt.privaterentalservice.services.solicitud;

import org.transportsgt.privaterentalservice.config.security.UsuarioAutenticado;
import org.transportsgt.privaterentalservice.dto.request.SolicitudAlquilerRequest;
import org.transportsgt.privaterentalservice.dto.request.SolicitudMostradorRequest;
import org.transportsgt.privaterentalservice.dto.response.AlquilerResponse;

/** Alta de alquileres: estima el precio con la tarifa vigente y publica AlquilerSolicitado. */
public interface SolicitudAlquilerService {

    /** Cliente en línea: el NIT y el nombre salen de su perfil (cliente_ref), no del cuerpo. */
    AlquilerResponse solicitarEnLinea(SolicitudAlquilerRequest request, UsuarioAutenticado usuario);

    /** SUC o CAJ en mostrador: el NIT (o CF) y el nombre los dicta el cliente. */
    AlquilerResponse solicitarMostrador(SolicitudMostradorRequest request, UsuarioAutenticado usuario);
}
