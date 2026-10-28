package org.transportsgt.ticketservice.services.abordaje.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.ticketservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.ticketservice.dto.response.PasajeroResponse;
import org.transportsgt.ticketservice.dto.response.PasajerosViajeResponse;
import org.transportsgt.ticketservice.mappers.AbordajeMapper;
import org.transportsgt.ticketservice.models.ViajeRef;
import org.transportsgt.ticketservice.repositories.BoletoRepository;
import org.transportsgt.ticketservice.services.abordaje.PasajerosViajeService;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasajerosViajeServiceImpl implements PasajerosViajeService {

    private final BoletoRepository boletoRepository;
    private final ValidadorAbordaje validador;
    private final AbordajeMapper abordajeMapper;

    @Override
    public PasajerosViajeResponse pasajeros(UsuarioAutenticado usuario, UUID idViaje) {
        ViajeRef viaje = validador.viajeDeSuSucursal(usuario, idViaje);
        List<PasajeroResponse> pasajeros = boletoRepository.findConfirmadosDelViaje(idViaje).stream()
                .map(abordajeMapper::toPasajero)
                .toList();
        int abordados = (int) pasajeros.stream().filter(pasajero -> pasajero.abordadoEn() != null).count();
        return new PasajerosViajeResponse(viaje.getId(), viaje.getNombreOrigen(), viaje.getNombreDestino(),
                viaje.getFechaHoraSalida(), viaje.getPlacaBus(), viaje.getEstado(), pasajeros.size(), abordados,
                pasajeros);
    }
}
