package org.transportsgt.customerwalletservice.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.transportsgt.customerwalletservice.models.MovimientoCartera;
import org.transportsgt.customerwalletservice.models.TipoMovimiento;
import org.transportsgt.eventos.payload.TipoReferencia;

import java.util.Optional;
import java.util.UUID;

public interface MovimientoCarteraRepository
        extends JpaRepository<MovimientoCartera, UUID>, JpaSpecificationExecutor<MovimientoCartera> {

    /** Recarga o pago repetido con la misma {@code Clave-Idempotencia}: se devuelve el original. */
    Optional<MovimientoCartera> findByClaveIdempotencia(String claveIdempotencia);

    /**
     * PAGO o REEMBOLSO de una compra o alquiler ({@code uq_movimiento_referencia}). Lo usan la idempotencia del pago,
     * {@code GET /cartera/pagos} y el reembolso cuando el evento no trae {@code idPago}.
     */
    Optional<MovimientoCartera> findByTipoAndTipoReferenciaAndIdReferencia(
            TipoMovimiento tipo, TipoReferencia tipoReferencia, UUID idReferencia);

    /** PAGO original de un reembolso, por el {@code idPago} del evento. */
    Optional<MovimientoCartera> findByIdAndTipo(UUID id, TipoMovimiento tipo);

    /** Historial de la cartera, el más reciente primero ({@code idx_movimiento_cartera}). */
    Page<MovimientoCartera> findByCarteraIdOrderByFechaMovimientoDescCreadoEnDesc(UUID idCartera, Pageable pageable);
}
