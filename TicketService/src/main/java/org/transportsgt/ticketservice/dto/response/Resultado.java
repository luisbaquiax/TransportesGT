package org.transportsgt.ticketservice.dto.response;

/**
 * Resultado de una operación idempotente: {@code creado = false} si la clave ya existía y se devuelve el original
 * (el controlador responde 200 en vez de 201).
 */
public record Resultado<T>(T valor, boolean creado) {

    public static <T> Resultado<T> nuevo(T valor) {
        return new Resultado<>(valor, true);
    }

    public static <T> Resultado<T> existente(T valor) {
        return new Resultado<>(valor, false);
    }
}
