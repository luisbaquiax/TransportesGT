package org.transportsgt.reportingservice.repositories.reportssucursal;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.transportsgt.reportingservice.models.RepIngreso;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Reporte 10: alquileres pagados (no anulados) de la sucursal; el bus sale del viaje del alquiler.
 */
public interface ReporteIngresosAlquileresRepository extends Repository<RepIngreso, UUID> {

    @Query(nativeQuery = true, value = """
            SELECT i.id_referencia AS "idAlquiler", i.nombre_cliente AS "cliente", i.nit_cliente AS "nit",
                   i.origen AS "origen", i.destino AS "destino", i.fecha_salida AS "fechaSalida",
                   i.fecha_retorno AS "fechaRetorno", b.placa AS "placa", i.fecha_ingreso AS "fechaPago",
                   i.monto AS "precioTotal"
              FROM rep_ingreso i
              LEFT JOIN rep_viaje v ON v.id_alquiler = i.id_referencia AND NOT v.eliminado
              LEFT JOIN rep_bus b   ON b.id = v.id_bus
             WHERE i.tipo_ingreso = 'ALQUILER' AND NOT i.anulado AND i.id_sucursal = :idSucursal
               AND (CAST(:desde AS date) IS NULL OR i.fecha_ingreso >= CAST(:desde AS date))
               AND (CAST(:hasta AS date) IS NULL OR i.fecha_ingreso <= CAST(:hasta AS date))
             ORDER BY i.fecha_ingreso, i.creado_en
            """)
    List<IngresoAlquilerConsulta> consultar(@Param("idSucursal") UUID idSucursal, @Param("desde") LocalDate desde,
                                            @Param("hasta") LocalDate hasta);
}
