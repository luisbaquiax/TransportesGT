package org.transportsgt.notificationservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.transportsgt.eventos.payload.Rol;

import java.util.UUID;

/**
 * Copia local de los usuarios (UsuarioCreado/Actualizado): correo de los clientes y de los administradores de
 * sucursal que reciben los avisos de licencia.
 */
@Entity
@Table(name = "contacto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contacto {

    @Id
    @Column(name = "id_usuario")
    private UUID idUsuario;

    @Column(name = "correo", nullable = false, length = 150)
    private String correo;

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 20)
    private Rol rol;

    @Column(name = "id_sucursal")
    private UUID idSucursal;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    /** versionAgregado del último evento aplicado. */
    @Column(name = "version", nullable = false)
    private Long version;
}
