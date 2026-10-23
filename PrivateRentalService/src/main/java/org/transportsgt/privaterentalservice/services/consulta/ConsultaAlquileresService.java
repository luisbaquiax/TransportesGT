package org.transportsgt.privaterentalservice.services.consulta;

import org.springframework.data.domain.Pageable;
import org.transportsgt.privaterentalservice.config.security.UsuarioAutenticado;
import org.transportsgt.privaterentalservice.dto.request.FiltroAlquileres;
import org.transportsgt.privaterentalservice.dto.response.AlquilerResponse;
import org.transportsgt.privaterentalservice.dto.response.PaginaResponse;

import java.util.UUID;

public interface ConsultaAlquileresService {

    /**
     * Con el historial de cotizaciones.
     */
    AlquilerResponse detalle(UUID id, UsuarioAutenticado usuario);

    PaginaResponse<AlquilerResponse> listar(FiltroAlquileres filtro, UsuarioAutenticado usuario, Pageable pageable);

    /**
     * En línea del usuario y los de mostrador hechos con el NIT de su perfil.
     */
    PaginaResponse<AlquilerResponse> mios(UsuarioAutenticado usuario, Pageable pageable);
}
