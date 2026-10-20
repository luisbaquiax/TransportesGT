package org.transportsgt.ticketservice.fixture;

import org.transportsgt.ticketservice.models.ViajeRef;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.BusAsignado;
import org.transportsgt.eventos.payload.ChoferAsignado;
import org.transportsgt.eventos.payload.DatosPerfilClienteActualizado;
import org.transportsgt.eventos.payload.DatosViaje;
import org.transportsgt.eventos.payload.DatosViajeEliminado;
import org.transportsgt.eventos.payload.EstadoViaje;
import org.transportsgt.eventos.payload.TipoViaje;

import java.time.Instant;
import java.util.UUID;

public final class EventoFixture {

    private EventoFixture() {
    }

    public static <T> SobreEvento<T> sobre(TipoEvento tipo, T datos, Long versionAgregado) {
        return new SobreEvento<>(UUID.randomUUID(), tipo.nombre(), 1, ViajeRefFixture.AHORA,
                tipo.productor().nombreServicio(), UUID.randomUUID(), versionAgregado, UUID.randomUUID(), datos);
    }

    /** Instantánea de un viaje con los datos de la proyección dada. */
    public static DatosViaje viaje(ViajeRef viaje, TipoViaje tipo, EstadoViaje estado) {
        BusAsignado bus = viaje.getIdBus() == null ? null
                : new BusAsignado(viaje.getIdBus(), viaje.getPlacaBus(), viaje.getCapacidadBus());
        return new DatosViaje(viaje.getId(), tipo, estado, viaje.getIdRuta(), viaje.getIdSucursalOrigen(),
                viaje.getNombreOrigen(), viaje.getNombreDestino(), tipo == TipoViaje.ALQUILER_PRIVADO ? UUID.randomUUID() : null,
                viaje.getFechaHoraSalida(), viaje.getFechaHoraLlegadaEstimada(), viaje.getPrecioBoleto(), bus,
                new ChoferAsignado(UUID.randomUUID(), "Juan Pérez"));
    }

    public static DatosViajeEliminado viajeEliminado(UUID idViaje) {
        return new DatosViajeEliminado(idViaje, TipoViaje.REGULAR, null, Instant.parse("2026-10-20T15:00:00Z"),
                UUID.randomUUID());
    }

    public static DatosPerfilClienteActualizado perfil(UUID idUsuario, String nit) {
        return new DatosPerfilClienteActualizado(idUsuario, nit, ClienteRefFixture.NOMBRE, true);
    }
}
