package org.transportsgt.identityservice.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.identityservice.models.Usuario;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID>, JpaSpecificationExecutor<Usuario> {

    /** Para el login: trae la sucursal para armar los claims del JWT. */
    @EntityGraph(attributePaths = "sucursal")
    Optional<Usuario> findByCorreoIgnoreCase(String correo);

    boolean existsByCorreoIgnoreCase(String correo);

    boolean existsByCorreoIgnoreCaseAndIdNot(String correo, UUID id);

    List<Usuario> findAllBySucursalIdOrderByNombreCompleto(UUID idSucursal);

    List<Usuario> findAllBySucursalIdAndRolOrderByNombreCompleto(UUID idSucursal, Rol rol);

    List<Usuario> findAllByRolOrderByNombreCompleto(Rol rol);

    long countBySucursalIdAndRolAndActivoTrue(UUID idSucursal, Rol rol);

    long countByRolAndActivoTrue(Rol rol);

    /** Republicación de eventos: todos los usuarios con su sucursal. */
    @EntityGraph(attributePaths = "sucursal")
    List<Usuario> findAllByOrderByCreadoEn();
}
