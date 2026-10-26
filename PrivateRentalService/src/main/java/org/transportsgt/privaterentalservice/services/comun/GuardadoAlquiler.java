package org.transportsgt.privaterentalservice.services.comun;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.repositories.AlquilerRepository;

/**
 * Guarda con flush para que el {@code @Version} ya esté incrementado al registrar el evento: los consumidores
 * descartan las versiones que no son mayores que la aplicada.
 */
@Component
@RequiredArgsConstructor
public class GuardadoAlquiler {

    private final AlquilerRepository alquilerRepository;

    public Alquiler guardar(Alquiler alquiler) {
        return alquilerRepository.saveAndFlush(alquiler);
    }
}
