package org.transportsgt.reportingservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.reportingservice.models.RepIngreso;
import org.transportsgt.reportingservice.models.TipoIngreso;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RepIngresoRepository extends JpaRepository<RepIngreso, UUID>, JpaSpecificationExecutor<RepIngreso> {

    /** {@code uq_rep_ingreso}: un ingreso por compra o alquiler. */
    boolean existsByTipoIngresoAndIdReferencia(TipoIngreso tipoIngreso, UUID idReferencia);

    Optional<RepIngreso> findByTipoIngresoAndIdReferencia(TipoIngreso tipoIngreso, UUID idReferencia);

    /** BoletosAnulados / AlquilerCancelado: revierte el ingreso sin borrarlo. */
    @Modifying
    @Query("""
            UPDATE RepIngreso i SET i.anulado = true, i.anuladoEn = :anuladoEn
             WHERE i.tipoIngreso = :tipoIngreso AND i.idReferencia = :idReferencia AND i.anulado = false
            """)
    int anular(@Param("tipoIngreso") TipoIngreso tipoIngreso, @Param("idReferencia") UUID idReferencia,
               @Param("anuladoEn") OffsetDateTime anuladoEn);
}
