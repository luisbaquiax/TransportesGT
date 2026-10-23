package org.transportsgt.privaterentalservice.services.pago.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.Canal;
import org.transportsgt.privaterentalservice.config.security.UsuarioAutenticado;
import org.transportsgt.privaterentalservice.dto.request.PagoAlquilerRequest;
import org.transportsgt.privaterentalservice.dto.response.AlquilerResponse;
import org.transportsgt.privaterentalservice.exception.SolicitudInvalidaException;
import org.transportsgt.privaterentalservice.mappers.AlquilerMapper;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;
import org.transportsgt.privaterentalservice.services.comun.BuscadorAlquileres;
import org.transportsgt.privaterentalservice.services.comun.TransicionesAlquiler;
import org.transportsgt.privaterentalservice.services.pago.ConfirmadorAlquiler;
import org.transportsgt.privaterentalservice.services.pago.EstrategiaPagoAlquiler;
import org.transportsgt.privaterentalservice.services.pago.PagoAlquilerService;
import org.transportsgt.privaterentalservice.dto.PagoRealizado;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mismo flujo para ambos canales; lo que cambia (quién paga y cómo se cobra) lo pone la estrategia del canal.
 */
@Service
public class PagoAlquilerServiceImpl implements PagoAlquilerService {

    private final Map<Canal, EstrategiaPagoAlquiler> estrategias = new EnumMap<>(Canal.class);
    private final BuscadorAlquileres buscador;
    private final TransicionesAlquiler transiciones;
    private final ConfirmadorAlquiler confirmador;
    private final AlquilerMapper mapper;

    public PagoAlquilerServiceImpl(List<EstrategiaPagoAlquiler> estrategias, BuscadorAlquileres buscador,
                                   TransicionesAlquiler transiciones, ConfirmadorAlquiler confirmador,
                                   AlquilerMapper mapper) {
        estrategias.forEach(e -> this.estrategias.put(e.canal(), e));
        this.buscador = buscador;
        this.transiciones = transiciones;
        this.confirmador = confirmador;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public AlquilerResponse pagarEnLinea(UUID id, PagoAlquilerRequest req, UsuarioAutenticado usuario,
                                         String authorizationHeader) {
        return pagar(Canal.EN_LINEA, id, req, usuario, authorizationHeader);
    }

    @Override
    @Transactional
    public AlquilerResponse pagarMostrador(UUID id, PagoAlquilerRequest req, UsuarioAutenticado usuario) {
        return pagar(Canal.MOSTRADOR, id, req, usuario, null);
    }

    private AlquilerResponse pagar(Canal canal, UUID id, PagoAlquilerRequest req, UsuarioAutenticado usuario,
                                   String authorizationHeader) {
        EstrategiaPagoAlquiler estrategia = estrategias.get(canal);
        Alquiler a = buscador.porId(id);
        estrategia.autorizar(a, usuario);
        if (a.getCanal() != canal) {
            throw new SolicitudInvalidaException(estrategias.get(a.getCanal()).indicacion());
        }
        transiciones.verificar(a, EstadoAlquiler.CONFIRMADO, "pagar (requiere COTIZADO)");
        PagoRealizado pago = estrategia.cobrar(a, req.fechaPago(), authorizationHeader);
        return mapper.toResponse(confirmador.confirmar(a, pago));
    }
}
