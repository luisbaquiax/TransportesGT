package org.transportsgt.customerwalletservice.dto.response;

import java.util.UUID;

/**
 * Lo que necesita el mostrador para enlazar una venta a un cliente (sin saldo ni DPI).
 */
public record ClienteBusquedaResponse(
        UUID idUsuario,
        String nombreCompleto,
        String nit,
        Boolean activo
) {
}
