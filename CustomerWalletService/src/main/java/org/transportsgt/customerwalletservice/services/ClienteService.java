package org.transportsgt.customerwalletservice.services;

import org.transportsgt.customerwalletservice.config.security.UsuarioAutenticado;
import org.transportsgt.customerwalletservice.dto.request.ActualizarPerfilRequest;
import org.transportsgt.customerwalletservice.dto.response.ClienteBusquedaResponse;
import org.transportsgt.customerwalletservice.dto.response.ClienteResponse;
import org.transportsgt.eventos.payload.DatosUsuario;

public interface ClienteService {

    /**
     * Aplica UsuarioCreado / UsuarioActualizado (cualquier rol; se tratan igual, como instantánea).
     * Si el cliente no existe lo crea con su cartera en saldo 0; si existe, solo aplica el evento si
     * {@code versionAgregado > version_usuario}. Publica PerfilClienteActualizado al crear y cuando cambia
     * el nombre o {@code activo} (un cambio de correo no lo publica).
     *
     * @return {@code true} si el evento se aplicó; {@code false} si era viejo o repetido
     */
    boolean aplicarUsuario(DatosUsuario datos, Long versionAgregado);

    ClienteResponse obtenerPerfil(UsuarioAutenticado usuario);

    /**
     * Actualiza NIT, DPI, teléfono y dirección. El NIT se normaliza y {@code CF} se rechaza.
     * Publica PerfilClienteActualizado si cambió el NIT.
     */
    ClienteResponse actualizarPerfil(UsuarioAutenticado usuario, ActualizarPerfilRequest request);

    /** Búsqueda de mostrador por NIT (se normaliza antes de buscar). */
    ClienteBusquedaResponse buscarPorNit(String nit);
}
