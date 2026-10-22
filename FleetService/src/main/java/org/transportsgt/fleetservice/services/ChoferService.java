package org.transportsgt.fleetservice.services;

import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;
import org.transportsgt.fleetservice.config.security.UsuarioAutenticado;
import org.transportsgt.fleetservice.dto.request.ActualizarChoferRequest;
import org.transportsgt.fleetservice.dto.request.CambiarActivoRequest;
import org.transportsgt.fleetservice.dto.request.CrearChoferRequest;
import org.transportsgt.fleetservice.dto.response.ChoferResponse;
import org.transportsgt.fleetservice.dto.response.PaginaResponse;

import java.util.UUID;

public interface ChoferService {

    /**
     * Crea el chofer en la sucursal del administrador. Publica ChoferRegistrado.
     */
    ChoferResponse crear(CrearChoferRequest request, UsuarioAutenticado autenticado);

    /**
     * Paginado. ADMIN_SUCURSAL solo ve los de su sucursal.
     */
    PaginaResponse<ChoferResponse> listar(UUID idSucursal, Boolean activo, String texto,
                                          Pageable pageable, UsuarioAutenticado autenticado);

    ChoferResponse obtener(UUID id, UsuarioAutenticado autenticado);

    /**
     * Actualiza los datos editables. Publica ChoferActualizado
     * (Notificaciones reprograma los avisos de licencia).
     */
    ChoferResponse actualizar(UUID id, ActualizarChoferRequest request, UsuarioAutenticado autenticado);

    /**
     * Activa o desactiva. Publica ChoferActualizado si cambia.
     */
    ChoferResponse cambiarActivo(UUID id, CambiarActivoRequest request, UsuarioAutenticado autenticado);

    /**
     * Guarda la foto (disco o bucket privado) y deja su referencia interna en foto_url.
     */
    ChoferResponse guardarFoto(UUID id, MultipartFile archivo, UsuarioAutenticado autenticado);

    /**
     * Foto privada del chofer, con la misma autorización por sucursal que al consultarlo. 404 si no tiene foto.
     */
    FotoService.FotoLeida leerFoto(UUID id, UsuarioAutenticado autenticado);
}
