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

    Optional<MovimientoCartera> findByClaveIdempotencia(String claveIdempotencia);

    Optional<MovimientoCartera> findByTipoAndTipoReferenciaAndIdReferencia(
            TipoMovimiento tipo, TipoReferencia tipoReferencia, UUID idReferencia);

    Optional<MovimientoCartera> findByIdAndTipo(UUID id, TipoMovimiento tipo);

    Page<MovimientoCartera> findByCarteraIdOrderByFechaMovimientoDescCreadoEnDesc(UUID idCartera, Pageable pageable);
}
