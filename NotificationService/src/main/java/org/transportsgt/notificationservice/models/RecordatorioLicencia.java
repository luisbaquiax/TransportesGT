package org.transportsgt.notificationservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Aviso programado de vencimiento de licencia (30, 15 y 3 días antes). La clave única
 * {@code (id_chofer, fecha_vencimiento, dias_anticipacion)} lo hace idempotente.
 */
@Entity
@Table(name = "recordatorio_licencia")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecordatorioLicencia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_chofer", nullable = false)
    private ChoferLicencia chofer;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    /** 30, 15 o 3. */
    @Column(name = "dias_anticipacion", nullable = false)
    private Short diasAnticipacion;

    @Column(name = "fecha_envio_programada", nullable = false)
    private LocalDate fechaEnvioProgramada;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoRecordatorio estado = EstadoRecordatorio.PENDIENTE;

    @Builder.Default
    @Column(name = "intentos", nullable = false)
    private Short intentos = 0;

    @Column(name = "enviado_en")
    private OffsetDateTime enviadoEn;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;
}
