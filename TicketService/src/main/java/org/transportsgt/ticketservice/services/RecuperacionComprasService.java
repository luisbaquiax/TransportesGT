package org.transportsgt.ticketservice.services;

import java.util.List;
import java.util.UUID;

/**
 * Recuperación de la saga: resuelve las compras que quedaron {@code RESERVADA} porque Cartera no respondió o porque el
 * servicio cayó entre el cobro y la confirmación.
 */
public interface RecuperacionComprasService {

    enum Resultado {
        /**
         * Cartera tenía el pago: la compra se confirmó (o se reembolsará si el viaje ya se eliminó).
         */
        CONFIRMADA,
        /**
         * No hubo pago y la reserva venció: se liberaron los asientos.
         */
        LIBERADA,
        /**
         * No hubo pago, pero la reserva sigue vigente, o Cartera no respondió: se revisa en el siguiente ciclo.
         */
        PENDIENTE
    }

    /**
     * Compras {@code RESERVADA} con la antigüedad mínima configurada.
     */
    List<UUID> pendientes();

    /**
     * Revisa una compra. Cada cambio va en su propia transacción: una falla no afecta a las demás.
     */
    Resultado recuperar(UUID idCompra);
}
