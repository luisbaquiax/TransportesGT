package org.transportsgt.fleetservice.services.foto;

import org.springframework.web.multipart.MultipartFile;
import org.transportsgt.fleetservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.fleetservice.dto.response.BusResponse;
import org.transportsgt.fleetservice.dto.response.ChoferResponse;

import java.util.Optional;
import java.util.UUID;

/**
 * Fotos de buses y choferes. Son privadas: se sirven por GET /buses/{id}/foto y GET /choferes/{id}/foto con
 * la misma autorización por sucursal que el registro. Dónde se guardan lo decide {@code FOTOS_NUBE_HABILITADA}:
 * disco local en desarrollo o Cloud Storage en producción (plan §5).
 */
public interface FotosFlotaService {

    /**
     * Valida y guarda la imagen y deja su referencia interna en foto_url.
     */
    BusResponse guardarFotoBus(UUID id, MultipartFile archivo, UsuarioAutenticado autenticado);

    /**
     * 404 si el bus no tiene foto.
     */
    FotoLeida leerFotoBus(UUID id, UsuarioAutenticado autenticado);

    ChoferResponse guardarFotoChofer(UUID id, MultipartFile archivo, UsuarioAutenticado autenticado);

    FotoLeida leerFotoChofer(UUID id, UsuarioAutenticado autenticado);

    /**
     * Ruta obsoleta GET /fotos/{tipo}/{nombre}: vacío si el nombre no es seguro o no existe.
     */
    Optional<FotoLeida> leerPorNombre(TipoFoto tipo, String nombre);
}
