package org.transportsgt.identityservice.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Página estable para el cliente (no expone la estructura interna de Spring Data).
 */
public record PaginaResponse<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas
) {

    public static <E, T> PaginaResponse<T> de(Page<E> pagina, Function<E, T> conversion) {
        return new PaginaResponse<>(
                pagina.getContent().stream().map(conversion).toList(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages());
    }
}
