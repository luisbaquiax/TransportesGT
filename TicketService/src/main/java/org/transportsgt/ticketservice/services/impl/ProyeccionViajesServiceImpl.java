package org.transportsgt.ticketservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.ticketservice.repositories.ViajeRefRepository;
import org.transportsgt.ticketservice.services.ProyeccionViajesService;
import org.transportsgt.eventos.payload.BusAsignado;
import org.transportsgt.eventos.payload.DatosViaje;
import org.transportsgt.eventos.payload.TipoViaje;

@Service
@RequiredArgsConstructor
public class ProyeccionViajesServiceImpl implements ProyeccionViajesService {

    private final ViajeRefRepository viajeRefRepository;

    @Override
    @Transactional
    public boolean aplicar(DatosViaje datos, Long versionAgregado) {
        if (datos.tipoViaje() != TipoViaje.REGULAR) {
            // Boletos solo vende viajes regulares
            return false;
        }
        BusAsignado bus = datos.bus();
        return viajeRefRepository.upsert(
                datos.idViaje(),
                datos.idRuta(),
                datos.idSucursalOrigen(),
                datos.nombreOrigen(),
                datos.nombreDestino(),
                datos.fechaHoraSalida(),
                datos.fechaHoraLlegadaEstimada(),
                datos.precioBoleto(),
                bus == null ? null : bus.idBus(),
                bus == null ? null : bus.placa(),
                bus == null ? null : (short) bus.capacidad(),
                datos.estado().name(),
                versionAgregado == null ? 0L : versionAgregado) == 1;
    }
}
