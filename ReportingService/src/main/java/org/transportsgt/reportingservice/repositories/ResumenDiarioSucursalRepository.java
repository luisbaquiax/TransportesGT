package org.transportsgt.reportingservice.repositories;

import org.springframework.data.repository.Repository;
import org.transportsgt.reportingservice.models.ResumenDiarioSucursal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Solo lectura sobre la vista {@code v_resumen_diario_sucursal}.
 */
public interface ResumenDiarioSucursalRepository extends Repository<ResumenDiarioSucursal, ResumenDiarioSucursal.Clave> {

    List<ResumenDiarioSucursal> findAllByOrderByFecha();

    List<ResumenDiarioSucursal> findAllByFechaBetweenOrderByFecha(LocalDate desde, LocalDate hasta);

    List<ResumenDiarioSucursal> findAllByIdSucursalAndFechaBetweenOrderByFecha(UUID idSucursal, LocalDate desde,
                                                                               LocalDate hasta);
}
