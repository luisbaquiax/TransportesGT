package org.transportsgt.fleetservice.mappers;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.transportsgt.fleetservice.dto.response.PaginaResponse;

import java.util.function.Function;

/**
 * Convierte una {@link Page} de Spring Data en {@link PaginaResponse}.
 */
@Component
public class FabricaPaginas {

    public <E, T> PaginaResponse<T> de(Page<E> pagina, Function<E, T> conversion) {
        return new PaginaResponse<>(
                pagina.getContent().stream().map(conversion).toList(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages());
    }
}
