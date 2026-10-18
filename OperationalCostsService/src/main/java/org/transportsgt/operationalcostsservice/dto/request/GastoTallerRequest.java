package org.transportsgt.operationalcostsservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import org.transportsgt.eventos.payload.TipoLinea;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record GastoTallerRequest(
        @NotNull UUID idBus,

        @NotNull @PastOrPresent(message = "no puede ser futura")
        LocalDate fechaServicio,

        @Size(max = 500)
        String observaciones,

        @NotEmpty(message = "debe tener al menos una línea")
        List<@Valid LineaTallerRequest> lineas
) {

    public record LineaTallerRequest(
            @NotNull UUID idCategoria,

            @NotBlank @Size(max = 250)
            String descripcion,

            @NotNull TipoLinea tipo,

            @NotNull @DecimalMin(value = "0", message = "debe ser mayor o igual a 0")
            BigDecimal monto
    ) {
    }
}
