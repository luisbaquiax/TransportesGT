package org.transportsgt.customerwalletservice.services;

import org.transportsgt.eventos.payload.TipoReferencia;

import java.time.LocalDate;
import java.util.UUID;

public interface ReembolsoService {

    /**
     * Devuelve a la cartera el monto del PAGO original de una compra o alquiler y publica CarteraReembolsada.
     * Se aplica aunque el cliente esté inactivo. Si no hay PAGO o el reembolso ya existe, no hace nada.
     *
     * @param idPago          PAGO original según el evento; si es {@code null} se busca por la referencia
     * @param fechaMovimiento fecha del evento en hora de Guatemala
     * @return {@code true} si se aplicó el reembolso
     */
    boolean reembolsar(TipoReferencia tipoReferencia, UUID idReferencia, UUID idPago, LocalDate fechaMovimiento,
                       String motivo);
}
