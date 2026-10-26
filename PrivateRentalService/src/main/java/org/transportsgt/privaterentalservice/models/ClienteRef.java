package org.transportsgt.privaterentalservice.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Perfil comercial del cliente (PerfilClienteActualizado). De aquí salen el NIT y el nombre en un alquiler en línea,
 * nunca del cuerpo de la petición.
 */
@Entity
@Table(name = "cliente_ref")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClienteRef {

    @Id
    @Column(name = "id_usuario")
    private UUID idUsuario;

    /** Normalizado; null mientras el perfil no tenga NIT (el alquiler en línea responde 422 PERFIL_INCOMPLETO). */
    @Column(name = "nit", length = 20)
    private String nit;

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    /** versionAgregado del último evento aplicado. */
    @Column(name = "version", nullable = false)
    private Long version;
}
