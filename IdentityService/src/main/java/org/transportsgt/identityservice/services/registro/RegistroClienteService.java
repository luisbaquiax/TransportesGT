package org.transportsgt.identityservice.services.registro;

import org.transportsgt.identityservice.dto.request.RegistroRequest;
import org.transportsgt.identityservice.dto.response.UsuarioResponse;

/** Autorregistro por internet de un cliente. */
public interface RegistroClienteService {

    /** Crea un usuario CLIENTE con la contraseña elegida. Publica UsuarioCreado. */
    UsuarioResponse registrar(RegistroRequest request);
}
