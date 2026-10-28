package org.transportsgt.ticketservice.services.abordaje.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.ticketservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.ticketservice.dto.response.AbordajeResponse;
import org.transportsgt.ticketservice.dto.response.Resultado;
import org.transportsgt.ticketservice.exception.BoletoNoEncontradoException;
import org.transportsgt.ticketservice.mappers.AbordajeMapper;
import org.transportsgt.ticketservice.models.Boleto;
import org.transportsgt.ticketservice.models.ViajeRef;
import org.transportsgt.ticketservice.repositories.BoletoRepository;
import org.transportsgt.ticketservice.services.abordaje.AbordajeService;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AbordajeServiceImpl implements AbordajeService {

    private final BoletoRepository boletoRepository;
    private final ValidadorAbordaje validador;
    private final AbordajeMapper abordajeMapper;
    private final Clock clock;

    @Override
    @Transactional
    public Resultado<AbordajeResponse> registrar(UsuarioAutenticado usuario, UUID idViaje, String codigo) {
        ViajeRef viaje = validador.viajeDeSuSucursal(usuario, idViaje);
        String normalizado = codigo.trim().toUpperCase(Locale.ROOT);
        Boleto boleto = boletoRepository.findByCodigoParaAbordar(normalizado)
                .orElseThrow(() -> new BoletoNoEncontradoException(normalizado));
        validador.boletoDelViaje(boleto, viaje);
        if (boleto.abordado()) {
            return Resultado.existente(abordajeMapper.toResponse(boleto));
        }
        validador.abordable(viaje);
        boleto.setAbordadoEn(OffsetDateTime.now(clock));
        boleto.setAbordadoPor(usuario.id());
        log.info("Boleto {} (asiento {}) abordó el viaje {}", boleto.getCodigo(), boleto.getNumeroAsiento(), idViaje);
        return Resultado.nuevo(abordajeMapper.toResponse(boleto));
    }
}
