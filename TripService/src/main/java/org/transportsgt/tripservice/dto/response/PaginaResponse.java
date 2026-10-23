package org.transportsgt.tripservice.dto.response;

import java.util.List;

/**
 * Página estable para el cliente (no expone la estructura interna de Spring Data), igual que en Boletos.
 * La arma {@link org.transportsgt.tripservice.mappers.FabricaPaginas}.
 */
public record PaginaResponse<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas
) {
}
