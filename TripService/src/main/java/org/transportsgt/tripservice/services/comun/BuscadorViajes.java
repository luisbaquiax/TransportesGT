package org.transportsgt.tripservice.services.comun;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.tripservice.exception.ViajeNoEncontradoException;
import org.transportsgt.tripservice.models.Viaje;
import org.transportsgt.tripservice.repositories.ViajeRepository;

import java.util.UUID;

/** Carga un viaje con su ruta o responde 404; lo comparten la consulta, la programación y los registros. */
@Component
@RequiredArgsConstructor
public class BuscadorViajes {

    private final ViajeRepository viajeRepository;

    public Viaje cargar(UUID id) {
        return viajeRepository.findConRutaById(id).orElseThrow(() -> new ViajeNoEncontradoException(id));
    }
}
