package org.transportsgt.tripservice.mappers;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.transportsgt.tripservice.dto.response.PaginaResponse;

import java.util.List;

/** Convierte una {@link Page} de Spring Data en {@link PaginaResponse} con el contenido ya mapeado. */
@Component
public class FabricaPaginas {

    public <E, T> PaginaResponse<T> de(Page<E> pagina, List<T> contenido) {
        return new PaginaResponse<>(contenido, pagina.getNumber(), pagina.getSize(),
                pagina.getTotalElements(), pagina.getTotalPages());
    }
}
