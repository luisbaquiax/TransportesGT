package org.transportsgt.notificationservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.notificationservice.models.Contacto;

import java.util.List;
import java.util.UUID;

public interface ContactoRepository extends JpaRepository<Contacto, UUID> {

    /** Administradores de sucursal activos que reciben los avisos de licencia de sus choferes. */
    List<Contacto> findAllByIdSucursalAndRolAndActivoTrue(UUID idSucursal, Rol rol);

    /**
     * Aplica UsuarioCreado/Actualizado. Ignora el evento si su versión no es mayor que la guardada.
     *
     * @param rol nombre del enum {@code Rol}
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO contacto (id_usuario, correo, nombre_completo, rol, id_sucursal, activo, version)
            VALUES (:idUsuario, :correo, :nombreCompleto, :rol, CAST(:idSucursal AS uuid), :activo, :version)
            ON CONFLICT (id_usuario) DO UPDATE
               SET correo = EXCLUDED.correo, nombre_completo = EXCLUDED.nombre_completo, rol = EXCLUDED.rol,
                   id_sucursal = EXCLUDED.id_sucursal, activo = EXCLUDED.activo, version = EXCLUDED.version
             WHERE contacto.version < EXCLUDED.version
            """)
    int upsert(@Param("idUsuario") UUID idUsuario, @Param("correo") String correo,
               @Param("nombreCompleto") String nombreCompleto, @Param("rol") String rol,
               @Param("idSucursal") UUID idSucursal, @Param("activo") boolean activo, @Param("version") long version);
}
