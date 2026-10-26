package org.transportsgt.ticketservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.ticketservice.models.ClienteRef;

import java.util.Optional;
import java.util.UUID;

public interface ClienteRefRepository extends JpaRepository<ClienteRef, UUID> {

    /**
     * Venta de mostrador con NIT pero sin nombre: si el NIT tiene cuenta, se usa el nombre de su perfil.
     */
    Optional<ClienteRef> findFirstByNit(String nit);

    /** Aplica PerfilClienteActualizado. Ignora el evento si su versión no es mayor que la guardada. */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO cliente_ref (id_usuario, nit, nombre_completo, activo, version)
            VALUES (:idUsuario, CAST(:nit AS varchar), :nombreCompleto, :activo, :version)
            ON CONFLICT (id_usuario) DO UPDATE
               SET nit = EXCLUDED.nit, nombre_completo = EXCLUDED.nombre_completo,
                   activo = EXCLUDED.activo, version = EXCLUDED.version
             WHERE cliente_ref.version < EXCLUDED.version
            """)
    int upsert(@Param("idUsuario") UUID idUsuario, @Param("nit") String nit,
               @Param("nombreCompleto") String nombreCompleto, @Param("activo") boolean activo,
               @Param("version") long version);
}
