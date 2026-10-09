package org.transportsgt.customerwalletservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.transportsgt.customerwalletservice.models.Cliente;

import java.util.Optional;
import java.util.UUID;

public interface ClienteRepository extends JpaRepository<Cliente, UUID> {

    /** El cliente del usuario autenticado y el que actualizan UsuarioCreado/Actualizado. */
    Optional<Cliente> findByIdUsuario(UUID idUsuario);

    boolean existsByIdUsuario(UUID idUsuario);

    /** NIT único ({@code uq_cliente_nit}); recibe el NIT ya normalizado. */
    boolean existsByNitAndIdNot(String nit, UUID id);

    /** DPI único ({@code uq_cliente_dpi}). */
    boolean existsByDpiAndIdNot(String dpi, UUID id);
}
