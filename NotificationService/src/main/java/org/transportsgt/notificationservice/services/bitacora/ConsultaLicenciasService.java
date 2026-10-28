package org.transportsgt.notificationservice.services.bitacora;

import org.transportsgt.notificationservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.notificationservice.dto.response.PaginaResponse;
import org.transportsgt.notificationservice.dto.response.RecordatorioLicenciaResponse;
import org.transportsgt.notificationservice.models.EstadoRecordatorio;

import java.util.UUID;

/**
 * Avisos de licencia programados. SIS ve todos; SUC solo los de su sucursal.
 */
public interface ConsultaLicenciasService {

    /**
     * @param idSucursal filtro opcional; un ADMIN_SUCURSAL que pide otra sucursal recibe 403 SIN_PERMISO
     */
    PaginaResponse<RecordatorioLicenciaResponse> listar(UsuarioAutenticado usuario, UUID idSucursal,
                                                        EstadoRecordatorio estado, int pagina, int tamano);
}
