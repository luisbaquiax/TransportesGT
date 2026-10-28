package org.transportsgt.ticketservice.services.consulta;

import org.transportsgt.ticketservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.ticketservice.dto.response.CompraResponse;
import org.transportsgt.ticketservice.dto.response.PaginaResponse;
import org.transportsgt.ticketservice.models.EstadoCompra;

import java.util.UUID;

/**
 * Consultas de compras con sus reglas de visibilidad.
 */
public interface ConsultaComprasService {

    /**
     * Historial: las compras del usuario y las de mostrador con su NIT. {@code estado} es opcional.
     */
    PaginaResponse<CompraResponse> mias(UsuarioAutenticado usuario, EstadoCompra estado, int pagina, int tamano);

    /**
     * El comprador ve las suyas; SUC y CAJ, las de su sucursal; SIS, todas. Si no le corresponde, 404.
     */
    CompraResponse detalle(UsuarioAutenticado usuario, UUID idCompra);

    /**
     * Compras de la sucursal del usuario (SUC, CAJ), con filtros opcionales.
     */
    PaginaResponse<CompraResponse> listado(UsuarioAutenticado usuario, UUID idViaje, EstadoCompra estado,
                                           int pagina, int tamano);
}
