package org.transportsgt.privaterentalservice.services.comun;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.privaterentalservice.exception.AlquilerNoEncontradoException;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.repositories.AlquilerRepository;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BuscadorAlquileres {

    private final AlquilerRepository alquilerRepository;

    public Alquiler porId(UUID id) {
        return alquilerRepository.findById(id).orElseThrow(() -> new AlquilerNoEncontradoException(id));
    }
}
