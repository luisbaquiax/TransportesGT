package org.transportsgt.tripservice.eventos;

import org.springframework.stereotype.Component;
import org.transportsgt.eventos.payload.flota.BusAsignado;
import org.transportsgt.eventos.payload.flota.ChoferAsignado;
import org.transportsgt.eventos.payload.viajes.DatosRuta;
import org.transportsgt.eventos.payload.viajes.DatosViaje;
import org.transportsgt.tripservice.models.Ruta;
import org.transportsgt.tripservice.models.Viaje;

/** Payloads del catálogo (§2.3) para rutas y viajes. */
@Component
public class MapeadorEventosViaje {

    public DatosRuta datosRuta(Ruta r) {
        return new DatosRuta(r.getId(), r.getIdSucursalOrigen(), r.getIdSucursalDestino(),
                r.getDistanciaKm(), r.getPrecioBoleto());
    }

    /** Instantánea completa del viaje; {@code bus} y {@code chofer} son null mientras esté pendiente de asignación. */
    public DatosViaje datosViaje(Viaje v, BusAsignado bus, ChoferAsignado chofer) {
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
