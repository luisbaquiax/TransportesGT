package org.transportsgt.customerwalletservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.customerwalletservice.exception.ClaveIdempotenciaRequeridaException;
import org.transportsgt.customerwalletservice.exception.ClaveIdempotenciaReutilizadaException;
import org.transportsgt.customerwalletservice.exception.CuentaInactivaException;
import org.transportsgt.customerwalletservice.exception.SaldoInsuficienteException;
import org.transportsgt.customerwalletservice.models.Cartera;
import org.transportsgt.customerwalletservice.models.Cliente;
import org.transportsgt.customerwalletservice.models.MovimientoCartera;
import org.transportsgt.customerwalletservice.models.TipoMovimiento;
import org.transportsgt.customerwalletservice.utils.Dinero;

import java.math.BigDecimal;

/**
 * Reglas de las operaciones que mueven dinero: {@code Clave-Idempotencia}, cuenta activa, operación repetida y
 * cambio de saldo (acreditar en recargas y reembolsos, debitar en pagos).
 */
@Component
@RequiredArgsConstructor
class ReglasCartera {

    static final int LARGO_MAXIMO_CLAVE = 100;

    private final Dinero dinero;

    /**
     * @return la clave sin espacios alrededor
     * @throws ClaveIdempotenciaRequeridaException si falta o supera {@value #LARGO_MAXIMO_CLAVE} caracteres
     */
    public String validarClave(String clave) {
        if (clave == null || clave.isBlank()) {
            throw new ClaveIdempotenciaRequeridaException();
        }
        String limpia = clave.trim();
        if (limpia.length() > LARGO_MAXIMO_CLAVE) {
            throw new ClaveIdempotenciaRequeridaException();
        }
        return limpia;
    }

    public void validarActiva(Cliente cliente) {
        if (!Boolean.TRUE.equals(cliente.getActivo())) {
            throw new CuentaInactivaException();
        }
    }

    /**
     * Operación repetida: solo es válida si es del mismo tipo, con la misma cartera y el mismo monto.
     */
    public MovimientoCartera verificarRepetido(MovimientoCartera original, TipoMovimiento tipo,
                                        Cartera cartera, BigDecimal monto) {
        if (original.getTipo() != tipo || !original.getCartera().getId().equals(cartera.getId())
                || !dinero.iguales(original.getMonto(), monto)) {
            throw new ClaveIdempotenciaReutilizadaException();
        }
        return original;
    }

    /**
     * Suma el monto al saldo de la cartera (recarga o reembolso).
     *
     * @return el saldo posterior, con 2 decimales
     */
    public BigDecimal acreditar(Cartera cartera, BigDecimal monto) {
        validarMonto(monto);
        BigDecimal saldoPosterior = dinero.escalar(cartera.getSaldo().add(monto));
        cartera.setSaldo(saldoPosterior);
        return saldoPosterior;
    }

    /**
     * Resta el monto del saldo de la cartera (pago). Si no alcanza, el saldo no cambia.
     *
     * @return el saldo posterior, con 2 decimales
     * @throws SaldoInsuficienteException si el saldo es menor que el monto
     */
    public BigDecimal debitar(Cartera cartera, BigDecimal monto) {
        validarMonto(monto);
        if (cartera.getSaldo().compareTo(monto) < 0) {
            throw new SaldoInsuficienteException();
        }
        BigDecimal saldoPosterior = dinero.escalar(cartera.getSaldo().subtract(monto));
        cartera.setSaldo(saldoPosterior);
        return saldoPosterior;
    }

    private void validarMonto(BigDecimal monto) {
        if (monto == null || monto.signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor que cero");
        }
    }
}
