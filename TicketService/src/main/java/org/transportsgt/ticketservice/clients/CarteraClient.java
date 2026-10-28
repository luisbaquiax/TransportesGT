package org.transportsgt.ticketservice.clients;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.transportsgt.ticketservice.clients.dto.PagoCarteraConsulta;
import org.transportsgt.ticketservice.clients.dto.PagoCarteraRequest;
import org.transportsgt.ticketservice.clients.dto.PagoCarteraResponse;
import org.transportsgt.eventos.payload.costos.TipoReferencia;

import java.util.UUID;

@HttpExchange("/cartera/pagos")
public interface CarteraClient {

    /**
     * @param autorizacion {@code Bearer <jwt del usuario>}
     * @param clave {@code Clave-Idempotencia} = idCompra
     */
    @PostExchange
    PagoCarteraResponse pagar(@RequestHeader(HttpHeaders.AUTHORIZATION) String autorizacion,
                              @RequestHeader("Clave-Idempotencia") String clave,
                              @RequestBody PagoCarteraRequest cuerpo);

    /**
     * @param claveInterna secreto de los jobs sin usuario ({@code X-Clave-Interna})
     */
    @GetExchange
    PagoCarteraConsulta consultar(@RequestHeader("X-Clave-Interna") String claveInterna,
                                  @RequestParam("tipoReferencia") TipoReferencia tipoReferencia,
                                  @RequestParam("idReferencia") UUID idReferencia);
}
