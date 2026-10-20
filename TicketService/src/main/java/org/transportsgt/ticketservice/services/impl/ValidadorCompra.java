package org.transportsgt.ticketservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.ticketservice.config.BoletosProperties;
import org.transportsgt.ticketservice.exception.AsientoInvalidoException;
import org.transportsgt.ticketservice.exception.CompraDuplicadaException;
import org.transportsgt.ticketservice.exception.CuentaInactivaException;
import org.transportsgt.ticketservice.exception.PerfilIncompletoException;
import org.transportsgt.ticketservice.exception.ViajeNoDisponibleException;
import org.transportsgt.ticketservice.models.Boleto;
import org.transportsgt.ticketservice.models.ClienteRef;
import org.transportsgt.ticketservice.models.Compra;
import org.transportsgt.ticketservice.models.ViajeRef;
import org.transportsgt.ticketservice.repositories.ClienteRefRepository;
import org.transportsgt.ticketservice.repositories.ViajeRefRepository;
import org.transportsgt.eventos.payload.Canal;
import org.transportsgt.eventos.payload.EstadoViaje;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Validaciones previas a una venta, sin reservar nada: comprador, viaje, asientos y repetición del {@code idCompra}.
 */
@Component
@RequiredArgsConstructor
class ValidadorCompra {

    private final ClienteRefRepository clienteRefRepository;
    private final ViajeRefRepository viajeRefRepository;
    private final BoletosProperties propiedades;
    private final Clock clock;

    /**
     * Comprador en línea: su perfil debe tener NIT y estar activo.
     */
    ClienteRef validarComprador(UUID idUsuario) {
        ClienteRef cliente = clienteRefRepository.findById(idUsuario).orElseThrow(PerfilIncompletoException::new);
        if (cliente.getNit() == null || cliente.getNit().isBlank()) {
            throw new PerfilIncompletoException();
        }
        if (!Boolean.TRUE.equals(cliente.getActivo())) {
            throw new CuentaInactivaException();
        }
        return cliente;
    }

    /**
     * Solo se venden viajes {@code PROGRAMADO}, no eliminados, con bus y con salida futura (según el {@code Clock}).
     */
    ViajeRef validarViaje(UUID idViaje) {
        ViajeRef viaje = viajeRefRepository.findById(idViaje)
                .orElseThrow(() -> new ViajeNoDisponibleException("El viaje no existe o no está a la venta"));
        if (Boolean.TRUE.equals(viaje.getEliminado())) {
            throw new ViajeNoDisponibleException("El viaje fue cancelado");
        }
        if (viaje.getEstado() != EstadoViaje.PROGRAMADO) {
            throw new ViajeNoDisponibleException("El viaje ya no está a la venta (estado " + viaje.getEstado() + ")");
        }
        if (!viaje.getFechaHoraSalida().isAfter(OffsetDateTime.now(clock))) {
            throw new ViajeNoDisponibleException("El viaje ya salió");
        }
        if (viaje.getCapacidadBus() == null) {
            throw new ViajeNoDisponibleException("El viaje aún no tiene bus asignado");
        }
        return viaje;
    }

    /**
     * Al menos uno, sin repetidos, dentro de la capacidad del bus y sin pasar el máximo por compra.
     *
     * @return los asientos ordenados
     */
    List<Short> validarAsientos(List<Integer> asientos, ViajeRef viaje) {
        if (asientos == null || asientos.isEmpty()) {
            throw new AsientoInvalidoException("Debe elegir al menos un asiento");
        }
        int maximo = propiedades.compra().maxAsientos();
        if (asientos.size() > maximo) {
            throw new AsientoInvalidoException("Máximo " + maximo + " asientos por compra");
        }
        Set<Integer> vistos = new HashSet<>();
        for (Integer asiento : asientos) {
            if (asiento == null || asiento < 1 || asiento > viaje.getCapacidadBus()) {
                throw new AsientoInvalidoException("El asiento " + asiento + " no existe: el bus tiene "
                        + viaje.getCapacidadBus() + " asientos");
            }
            if (!vistos.add(asiento)) {
                throw new AsientoInvalidoException("El asiento " + asiento + " está repetido");
            }
        }
        return asientos.stream().sorted().map(Integer::shortValue).toList();
    }

    /**
     * Un {@code idCompra} repetido solo es válido con el mismo canal, el mismo usuario (comprador o vendedor), el mismo
     * viaje y los mismos asientos.
     */
    void verificarRepetida(Compra existente, Canal canal, UUID idUsuario, UUID idViaje, List<Integer> asientos) {
        UUID dueno = canal == Canal.EN_LINEA ? existente.getIdUsuarioComprador() : existente.getIdUsuarioVendedor();
        Set<Integer> pedidos = asientos == null ? Set.of() : new HashSet<>(asientos);
        Set<Integer> guardados = new HashSet<>(existente.getBoletos().stream()
                .map(Boleto::getNumeroAsiento).map(Short::intValue).toList());
        if (existente.getCanal() != canal || !idUsuario.equals(dueno) || !existente.getIdViaje().equals(idViaje)
                || !guardados.equals(pedidos)) {
            throw new CompraDuplicadaException();
        }
    }
}
