package org.transportsgt.ticketservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un asiento de una {@link Compra}. El índice único parcial {@code uq_asiento_activo} impide vender dos veces el
 * mismo asiento, incluso con compras simultáneas.
 */
@Entity
@Table(name = "boleto")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Boleto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_compra", nullable = false, updatable = false)
    private Compra compra;

    /**
     * viaje_ref(id).
     */
    @Column(name = "id_viaje", nullable = false, updatable = false)
    private UUID idViaje;

    @Column(name = "numero_asiento", nullable = false, updatable = false)
    private Short numeroAsiento;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 12)
    private EstadoBoleto estado = EstadoBoleto.RESERVADO;

    @Column(name = "codigo", nullable = false, unique = true, updatable = false, length = 20)
    private String codigo;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    /**
     * Hora en que el pasajero abordó; null si todavía no.
     */
    @Column(name = "abordado_en")
    private OffsetDateTime abordadoEn;

    /**
     * Usuario (SUC o CAJ) que registró el abordaje.
     */
    @Column(name = "abordado_por")
    private UUID abordadoPor;

    public boolean abordado() {
        return abordadoEn != null;
    }
}
