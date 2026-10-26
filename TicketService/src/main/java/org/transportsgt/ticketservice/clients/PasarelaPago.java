package org.transportsgt.ticketservice.clients;

import org.transportsgt.ticketservice.exception.CarteraNoDisponibleException;
import org.transportsgt.ticketservice.exception.PagoRechazadoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de cobro que usa la saga. No sabe de HTTP: la implementación ({@link CarteraPasarelaPago}) se puede cambiar
 * sin tocar los servicios, y en las pruebas unitarias se reemplaza por un mock.
 */
public interface PasarelaPago {

    /**
     * Cobra una compra con el JWT del comprador. Es idempotente: la misma compra no se cobra dos veces.
     *
     * @return el {@code idPago}
     * @throws PagoRechazadoException       si Cartera rechaza el cobro (saldo, perfil, cuenta inactiva...)
     * @throws CarteraNoDisponibleException si no se sabe si el cobro salió (tiempo agotado, 5xx, circuito abierto)
     */
    UUID cobrar(String jwtComprador, UUID idCompra, BigDecimal monto, LocalDate fechaPago, String descripcion);

    /**
     * Consulta si la compra se cobró (job de recuperación, sin usuario).
     *
     * @return el {@code idPago}, o vacío si no hubo pago
     * @throws CarteraNoDisponibleException si Cartera no responde
     */
    Optional<UUID> consultarPago(UUID idCompra);
}
