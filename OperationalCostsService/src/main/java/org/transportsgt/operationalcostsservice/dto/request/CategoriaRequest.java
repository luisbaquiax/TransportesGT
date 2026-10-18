package org.transportsgt.operationalcostsservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoriaRequest(
        @NotBlank @Size(max = 80)
        String nombre,

        @Size(max = 250)
        String descripcion
) {
}
