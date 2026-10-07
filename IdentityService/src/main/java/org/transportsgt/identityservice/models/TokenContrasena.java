package org.transportsgt.identityservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.transportsgt.eventos.payload.PropositoContrasena;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "token_contrasena")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenContrasena {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "hash_token", nullable = false, unique = true, length = 64)
    private String hashToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "proposito", nullable = false, length = 20)
    private PropositoContrasena proposito;

    @Column(name = "expira_en", nullable = false)
    private OffsetDateTime expiraEn;

    @Column(name = "usado_en")
    private OffsetDateTime usadoEn;

    @Column(name = "invalidado_en")
    private OffsetDateTime invalidadoEn;

    @Column(name = "ip_solicitud", length = 45)
    private String ipSolicitud;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    public boolean estaVigente(OffsetDateTime ahora) {
        return usadoEn == null && invalidadoEn == null && expiraEn.isAfter(ahora);
    }
}
