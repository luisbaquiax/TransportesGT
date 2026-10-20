package org.transportsgt.tripservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.RegistroOutbox;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.BusAsignado;
import org.transportsgt.eventos.payload.ChoferAsignado;
import org.transportsgt.eventos.payload.DatosLlegada;
import org.transportsgt.eventos.payload.DatosRuta;
import org.transportsgt.eventos.payload.DatosRutaEliminada;
import org.transportsgt.eventos.payload.DatosSalida;
import org.transportsgt.eventos.payload.DatosViaje;
import org.transportsgt.eventos.payload.DatosViajeEliminado;
import org.transportsgt.tripservice.models.Ruta;
import org.transportsgt.tripservice.models.Viaje;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Guarda los eventos de rutas y viajes en el outbox (misma transacción que el cambio).
 * Los payloads de viaje se construyen aquí con bus/chofer resueltos para incluir placa, capacidad y nombre.
 */
@Component
@RequiredArgsConstructor
public class EventosViaje {

    private final RegistroOutbox outbox;

    public void rutaCreada(Ruta r) {
        outbox.registrar(TipoEvento.RUTA_CREADA, r.getId(), r.getVersion(), toDatosRuta(r));
    }

    public void rutaActualizada(Ruta r) {
        outbox.registrar(TipoEvento.RUTA_ACTUALIZADA, r.getId(), r.getVersion(), toDatosRuta(r));
    }

    public void rutaEliminada(UUID idRuta) {
        outbox.registrar(TipoEvento.RUTA_ELIMINADA, idRuta, null,
                new DatosRutaEliminada(idRuta, Instant.now()));
    }

    public void viajeProgramado(Viaje v, BusAsignado bus, ChoferAsignado chofer) {
        outbox.registrar(TipoEvento.VIAJE_PROGRAMADO, v.getId(), v.getVersion(), toDatosViaje(v, bus, chofer));
    }

    public void viajeActualizado(Viaje v, BusAsignado bus, ChoferAsignado chofer) {
        outbox.registrar(TipoEvento.VIAJE_ACTUALIZADO, v.getId(), v.getVersion(), toDatosViaje(v, bus, chofer));
    }

    public void viajeEliminado(Viaje v, UUID idUsuarioEliminador) {
        outbox.registrar(TipoEvento.VIAJE_ELIMINADO, v.getId(), v.getVersion(),
                new DatosViajeEliminado(v.getId(), v.getTipoViaje(), v.getIdAlquiler(), Instant.now(),
                        idUsuarioEliminador));
    }

    public void salidaRegistrada(Viaje v, OffsetDateTime fechaHoraSalidaReal, BigDecimal kilometrajeInicial,
                                 UUID idUsuarioRegistro) {
        outbox.registrar(TipoEvento.SALIDA_REGISTRADA, v.getId(), v.getVersion(),
                new DatosSalida(v.getId(), v.getIdBus(), v.getIdChofer(), fechaHoraSalidaReal, kilometrajeInicial,
                        idUsuarioRegistro));
    }

    public void llegadaRegistrada(Viaje v, UUID idSucursalBus, BigDecimal salarioBaseChofer,
                                  OffsetDateTime fechaHoraSalidaReal, OffsetDateTime fechaHoraLlegadaReal,
                                  BigDecimal kilometrajeInicial, BigDecimal kilometrajeFinal,
                                  BigDecimal distanciaRecorridaKm, BigDecimal gastoCombustibleTotal,
                                  UUID idUsuarioRegistro) {
        outbox.registrar(TipoEvento.LLEGADA_REGISTRADA, v.getId(), v.getVersion(),
                new DatosLlegada(v.getId(), v.getTipoViaje(), v.getIdAlquiler(),
                        v.getRuta() == null ? null : v.getRuta().getId(),
                        v.getIdSucursalOrigen(), v.getIdBus(), idSucursalBus, v.getIdChofer(),
                        salarioBaseChofer, fechaHoraSalidaReal, fechaHoraLlegadaReal,
                        kilometrajeInicial, kilometrajeFinal, distanciaRecorridaKm,
                        gastoCombustibleTotal, idUsuarioRegistro));
    }

    public static DatosRuta toDatosRuta(Ruta r) {
        return new DatosRuta(r.getId(), r.getIdSucursalOrigen(), r.getIdSucursalDestino(),
                r.getDistanciaKm(), r.getPrecioBoleto());
    }

    public static DatosViaje toDatosViaje(Viaje v, BusAsignado bus, ChoferAsignado chofer) {
        return new DatosViaje(
                v.getId(),
                v.getTipoViaje(),
                v.getEstado(),
                v.getRuta() == null ? null : v.getRuta().getId(),
                v.getIdSucursalOrigen(),
                v.getNombreOrigen(),
                v.getNombreDestino(),
                v.getIdAlquiler(),
                v.getFechaHoraSalida(),
                v.getFechaHoraLlegadaEstimada(),
                v.getPrecioBoleto(),
                bus,
                chofer);
    }
}
