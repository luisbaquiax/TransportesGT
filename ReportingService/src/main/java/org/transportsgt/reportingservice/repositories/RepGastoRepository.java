package org.transportsgt.reportingservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.transportsgt.reportingservice.models.RepGasto;
import org.transportsgt.reportingservice.models.TipoGasto;

import java.util.UUID;

public interface RepGastoRepository extends JpaRepository<RepGasto, UUID>, JpaSpecificationExecutor<RepGasto> {

    /** {@code uq_rep_gasto}: un registro por gasto, línea de taller, depreciación o pago. */
    boolean existsByTipoGastoAndIdReferencia(TipoGasto tipoGasto, UUID idReferencia);
}
