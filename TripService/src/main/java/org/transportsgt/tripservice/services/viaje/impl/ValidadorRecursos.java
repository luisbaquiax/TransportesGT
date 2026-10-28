package org.transportsgt.tripservice.services.viaje.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.payload.flota.EstadoOperativo;
import org.transportsgt.tripservice.exception.ReglaNegocioException;
import org.transportsgt.tripservice.exception.SolicitudInvalidaException;
import org.transportsgt.tripservice.models.BusRef;
import org.transportsgt.tripservice.models.ChoferRef;
import org.transportsgt.tripservice.repositories.BusRefRepository;
import org.transportsgt.tripservice.repositories.ChoferRefRepository;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class ValidadorRecursos {

    /**
     * La licencia vale hasta su día de vencimiento inclusive, en hora de Guatemala.
     */
    private static final ZoneId ZONA = ZoneId.of("America/Guatemala");

    private final BusRefRepository busRefRepository;
    private final ChoferRefRepository choferRefRepository;

    void validarHorario(OffsetDateTime salida, OffsetDateTime llegadaEstimada) {
        if (!llegadaEstimada.isAfter(salida)) {
            throw new SolicitudInvalidaException("La llegada estimada debe ser posterior a la salida");
        }
    }

    void validar(UUID idBus, UUID idChofer, UUID idSucursalViaje, OffsetDateTime llegadaEstimada,
                 Short numeroPasajeros) {
        if (idBus != null) {
            validarBus(idBus, idSucursalViaje, numeroPasajeros);
        }
        if (idChofer != null) {
            validarChofer(idChofer, idSucursalViaje, llegadaEstimada);
        }
    }

    private void validarBus(UUID idBus, UUID idSucursalViaje, Short numeroPasajeros) {
        BusRef bus = busRefRepository.findById(idBus)
                .orElseThrow(() -> new ReglaNegocioException("BUS_NO_ENCONTRADO", "El bus " + idBus + " no existe"));
        if (!bus.getActivo()) {
            throw new ReglaNegocioException("BUS_INACTIVO", "El bus asignado está inactivo");
        }
        if (!bus.getIdSucursal().equals(idSucursalViaje)) {
            throw new ReglaNegocioException("BUS_OTRA_SUCURSAL", "El bus no pertenece a la sucursal del viaje");
        }
        if (bus.getEstadoOperativo() == EstadoOperativo.EN_TALLER
                || bus.getEstadoOperativo() == EstadoOperativo.FUERA_DE_SERVICIO) {
            throw new ReglaNegocioException("BUS_NO_DISPONIBLE", "El bus está en taller o fuera de servicio");
        }
        if (numeroPasajeros != null && bus.getCapacidadPasajeros() < numeroPasajeros) {
            throw new ReglaNegocioException("CAPACIDAD_INSUFICIENTE",
                    "El bus (" + bus.getCapacidadPasajeros() + " asientos) no alcanza para "
                            + numeroPasajeros + " pasajeros");
        }
    }

    private void validarChofer(UUID idChofer, UUID idSucursalViaje, OffsetDateTime llegadaEstimada) {
        ChoferRef chofer = choferRefRepository.findById(idChofer)
                .orElseThrow(() -> new ReglaNegocioException("CHOFER_NO_ENCONTRADO",
                        "El chofer " + idChofer + " no existe"));
        if (!chofer.getActivo()) {
            throw new ReglaNegocioException("CHOFER_INACTIVO", "El chofer asignado está inactivo");
        }
        if (!chofer.getIdSucursal().equals(idSucursalViaje)) {
            throw new ReglaNegocioException("CHOFER_OTRA_SUCURSAL", "El chofer no pertenece a la sucursal del viaje");
        }
        if (chofer.getFechaVencimientoLicencia() != null && llegadaEstimada != null
                && chofer.getFechaVencimientoLicencia().isBefore(llegadaEstimada.atZoneSameInstant(ZONA).toLocalDate())) {
            throw new ReglaNegocioException("LICENCIA_VENCIDA",
                    "La licencia del chofer vence antes de finalizar el viaje");
        }
    }
}
