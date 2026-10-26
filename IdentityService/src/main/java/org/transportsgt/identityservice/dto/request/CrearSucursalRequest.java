package org.transportsgt.identityservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * La sucursal y su primer ADMIN_SUCURSAL se crean juntos (toda sucursal tiene al menos un administrador).
 */
public record CrearSucursalRequest(
        @NotNull @Valid SucursalRequest sucursal,
        @NotNull @Valid NuevoAdministradorRequest administrador
) {
}
