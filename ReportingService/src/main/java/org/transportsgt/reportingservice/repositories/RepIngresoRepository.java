package org.transportsgt.reportingservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.reportingservice.models.RepIngreso;
import org.transportsgt.reportingservice.models.TipoIngreso;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface RepIngresoRepository extends JpaRepository<RepIngreso, UUID> {

    /** {@code uq_rep_ingreso}: un ingreso por compra o alquiler. */
    boolean existsByTipoIngresoAndIdReferencia(TipoIngreso tipoIngreso, UUID idReferencia);

    /**
     * VentaBoletosRegistrada / AlquilerConfirmado (hechos). Un duplicado no hace nada.
     *
     * @param tipoIngreso nombre del enum {@link TipoIngreso}
     * @param canal       nombre del enum {@code Canal}
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO rep_ingreso (tipo_ingreso, id_referencia, id_sucursal, fecha_ingreso, monto, id_viaje, id_ruta,
                                     cantidad_boletos, canal, nit_cliente, nombre_cliente, origen, destino,
                                     fecha_salida, fecha_retorno)
            VALUES (:tipoIngreso, :idReferencia, :idSucursal, :fechaIngreso, :monto, CAST(:idViaje AS uuid),
                    CAST(:idRuta AS uuid), CAST(:cantidadBoletos AS smallint), CAST(:canal AS varchar),
                    CAST(:nitCliente AS varchar), CAST(:nombreCliente AS varchar), CAST(:origen AS varchar),
                    CAST(:destino AS varchar), CAST(:fechaSalida AS timestamptz), CAST(:fechaRetorno AS timestamptz))
            ON CONFLICT (tipo_ingreso, id_referencia) DO NOTHING
            """)
    int insertar(@Param("tipoIngreso") String tipoIngreso, @Param("idReferencia") UUID idReferencia,
                 @Param("idSucursal") UUID idSucursal, @Param("fechaIngreso") LocalDate fechaIngreso,
                 @Param("monto") BigDecimal monto, @Param("idViaje") UUID idViaje, @Param("idRuta") UUID idRuta,
                 @Param("cantidadBoletos") Integer cantidadBoletos, @Param("canal") String canal,
                 @Param("nitCliente") String nitCliente, @Param("nombreCliente") String nombreCliente,
                 @Param("origen") String origen, @Param("destino") String destino,
                 @Param("fechaSalida") OffsetDateTime fechaSalida, @Param("fechaRetorno") OffsetDateTime fechaRetorno);

    /** BoletosAnulados / AlquilerCancelado: revierte el ingreso sin borrarlo (queda en su fecha de pago original). */
    @Modifying
    @Query("""
            UPDATE RepIngreso i SET i.anulado = true, i.anuladoEn = :anuladoEn
             WHERE i.tipoIngreso = :tipoIngreso AND i.idReferencia = :idReferencia AND i.anulado = false
            """)
    int anular(@Param("tipoIngreso") TipoIngreso tipoIngreso, @Param("idReferencia") UUID idReferencia,
               @Param("anuladoEn") OffsetDateTime anuladoEn);
}
