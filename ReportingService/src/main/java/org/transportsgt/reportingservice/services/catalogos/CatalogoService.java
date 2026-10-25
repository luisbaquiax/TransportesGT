package org.transportsgt.reportingservice.services.catalogos;

import org.transportsgt.reportingservice.config.security.UsuarioAutenticado;
import org.transportsgt.reportingservice.dto.response.catalogos.OpcionCatalogo;

import java.util.List;
import java.util.UUID;

/**
 * Listas para llenar los filtros de los reportes en el frontend.
 */
public interface CatalogoService {

    /** Todas las sucursales en orden alfabético (ADMIN_SISTEMA). */
    List<OpcionCatalogo> sucursales();

    /** Rutas que salen de la sucursal; el ADMIN_SUCURSAL solo ve las de la suya. */
    List<OpcionCatalogo> rutas(UsuarioAutenticado usuario, UUID idSucursal);

    /** Buses de la sucursal; el ADMIN_SUCURSAL solo ve los de la suya. */
    List<OpcionCatalogo> buses(UsuarioAutenticado usuario, UUID idSucursal);
}
