package org.transportsgt.ticketservice.dto.response;

import org.transportsgt.ticketservice.models.EstadoBoleto;

public record BoletoResponse(Short numeroAsiento, String codigo, EstadoBoleto estado) {
}
