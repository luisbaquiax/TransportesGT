package org.transportsgt.privaterentalservice.services.tarifa.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.privaterentalservice.config.security.UsuarioAutenticado;
import org.transportsgt.privaterentalservice.dto.request.TarifaRequest;
import org.transportsgt.privaterentalservice.dto.response.TarifaResponse;
import org.transportsgt.privaterentalservice.exception.AlquilerException;
import org.transportsgt.privaterentalservice.exception.SolicitudInvalidaException;
import org.transportsgt.privaterentalservice.mappers.TarifaMapper;
import org.transportsgt.privaterentalservice.models.TarifaAlquiler;
import org.transportsgt.privaterentalservice.repositories.TarifaAlquilerRepository;
import org.transportsgt.privaterentalservice.services.comun.CalendarioGuatemala;
import org.transportsgt.privaterentalservice.services.tarifa.TarifaAlquilerService;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TarifaAlquilerServiceImpl implements TarifaAlquilerService {

    private final TarifaAlquilerRepository tarifaRepository;
    private final CalendarioGuatemala calendario;
    private final TarifaMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public TarifaResponse vigente(LocalDate fecha) {
        LocalDate dia = fecha == null ? calendario.hoy() : fecha;
        return tarifaRepository.findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(dia)
                .map(mapper::toResponse)
                .orElseThrow(() -> new AlquilerException(HttpStatus.NOT_FOUND, "SIN_TARIFA",
                        "No hay tarifa de alquiler vigente al " + dia));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TarifaResponse> historial() {
        return mapper.toResponseList(tarifaRepository.findAllByOrderByVigenteDesdeDesc());
    }

    @Override
    @Transactional
    public TarifaResponse registrar(TarifaRequest req, UsuarioAutenticado usuario) {
        // Las estimaciones ya hechas usaron las tarifas pasadas: no se reescriben
        if (req.vigenteDesde().isBefore(calendario.hoy())) {
            throw new SolicitudInvalidaException("La tarifa no puede tener vigencia en una fecha pasada");
        }
        TarifaAlquiler tarifa = tarifaRepository.findByVigenteDesde(req.vigenteDesde())
                .orElseGet(() -> TarifaAlquiler.builder().vigenteDesde(req.vigenteDesde()).build());
        tarifa.setPrecioPorKm(req.precioPorKm());
        tarifa.setPrecioPorDia(req.precioPorDia());
        tarifa.setCreadoPor(usuario.id());
        tarifa = tarifaRepository.saveAndFlush(tarifa);
        log.info("Tarifa de alquiler vigente desde {} registrada por {}: {}/km, {}/día",
                tarifa.getVigenteDesde(), usuario.id(), tarifa.getPrecioPorKm(), tarifa.getPrecioPorDia());
        return mapper.toResponse(tarifa);
    }
}
