package org.transportsgt.customerwalletservice.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.customerwalletservice.models.Cartera;

import java.util.Optional;
import java.util.UUID;

public interface CarteraRepository extends JpaRepository<Cartera, UUID> {

    /** Consulta de saldo (sin bloqueo). Trae el cliente para validar {@code activo}. */
    @EntityGraph(attributePaths = "cliente")
    Optional<Cartera> findByClienteIdUsuario(UUID idUsuario);

    /**
     * Recargas y pagos: {@code SELECT ... FOR UPDATE} serializa los movimientos simultáneos de la misma cartera,
     * así el saldo se valida y se descuenta sin carreras.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cartera c JOIN FETCH c.cliente WHERE c.cliente.idUsuario = :idUsuario")
    Optional<Cartera> findByIdUsuarioParaActualizar(@Param("idUsuario") UUID idUsuario);

    /** Reembolsos: la cartera sale del PAGO original, no del usuario del evento. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cartera c JOIN FETCH c.cliente WHERE c.id = :id")
    Optional<Cartera> findByIdParaActualizar(@Param("id") UUID id);
}
