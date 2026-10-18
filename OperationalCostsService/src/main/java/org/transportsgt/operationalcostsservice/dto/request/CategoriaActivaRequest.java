package org.transportsgt.operationalcostsservice.dto.request;

import jakarta.validation.constraints.NotNull;

public record CategoriaActivaRequest(
        @NotNull
        Boolean activa
) {
}
