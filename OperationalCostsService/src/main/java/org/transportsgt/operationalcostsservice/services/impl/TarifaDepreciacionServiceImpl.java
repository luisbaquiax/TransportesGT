package org.transportsgt.operationalcostsservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.operationalcostsservice.config.security.PoliticaAccesoSucursal;
import org.transportsgt.operationalcostsservice.config.security.UsuarioAutenticado;
import org.transportsgt.operationalcostsservice.dto.request.TarifaDepreciacionRequest;
import org.transportsgt.operationalcostsservice.dto.response.TarifaDepreciacionResponse;
import org.transportsgt.operationalcostsservice.eventos.EventosCostos;
import org.transportsgt.operationalcostsservice.exception.NoEncontradoException;
import org.transportsgt.operationalcostsservice.mappers.CostosMapper;
import org.transportsgt.operationalcostsservice.models.ConfiguracionDepreciacion;
import org.transportsgt.operationalcostsservice.repositories.ConfiguracionDepreciacionRepository;
import org.transportsgt.operationalcostsservice.services.TarifaDepreciacionService;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TarifaDepreciacionServiceImpl implements TarifaDepreciacionService {

    private final ConfiguracionDepreciacionRepository tarifaRepository;
    private final CostosMapper mapper;
    private final EventosCostos eventos;
    private final Clock clock;
    private final PoliticaAccesoSucursal politica;

    @Override
    @Transactional(readOnly = true)
    public TarifaDepreciacionResponse tarifaVigente(LocalDate fecha) {
        LocalDate f = fecha != null ? fecha : LocalDate.now(clock);
        return tarifaRepository.findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(f)
                .map(mapper::toResponse)
                .orElseThrow(() -> new NoEncontradoException("SIN_TARIFA",
                        "No hay tarifa de depreciación vigente para la fecha " + f));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TarifaDepreciacionResponse> historialTarifas() {
        return mapper.toTarifaList(tarifaRepository.findAllByOrderByVigenteDesdeDesc());
    }

    @Override
    @Transactional
    public TarifaDepreciacionResponse cambiarTarifa(TarifaDepreciacionRequest request, UsuarioAutenticado usuario) {
        politica.exigirAdminSistema(usuario,
                "Solo ADMIN_SISTEMA puede cambiar la tarifa de depreciación");
        tarifaRepository.guardarTarifa(request.montoPorKm(), request.vigenteDesde(), usuario.id());
        tarifaRepository.flush();
        ConfiguracionDepreciacion tarifa = tarifaRepository
                .findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(request.vigenteDesde())
                .orElseThrow(() -> new NoEncontradoException("SIN_TARIFA", "Tarifa no guardada"));
        eventos.tarifaDepreciacionCambiada(mapper.toDatosTarifa(
                request.montoPorKm(), request.vigenteDesde(), usuario.id()));
        return mapper.toResponse(tarifa);
    }
}
