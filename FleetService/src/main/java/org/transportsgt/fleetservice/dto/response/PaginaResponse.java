package org.transportsgt.fleetservice.dto.response;


import java.util.List;

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
}
