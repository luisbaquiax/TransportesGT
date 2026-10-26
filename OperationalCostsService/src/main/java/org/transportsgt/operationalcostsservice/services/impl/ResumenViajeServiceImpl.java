package org.transportsgt.operationalcostsservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.operationalcostsservice.config.security.PoliticaAccesoSucursal;
import org.transportsgt.operationalcostsservice.config.security.UsuarioAutenticado;
import org.transportsgt.operationalcostsservice.dto.response.ResumenCostosViajeResponse;
import org.transportsgt.operationalcostsservice.exception.NoEncontradoException;
import org.transportsgt.operationalcostsservice.mappers.CostosMapper;
import org.transportsgt.operationalcostsservice.models.GastoCombustible;
import org.transportsgt.operationalcostsservice.models.PagoChofer;
import org.transportsgt.operationalcostsservice.models.RegistroDepreciacion;
import org.transportsgt.operationalcostsservice.repositories.GastoCombustibleRepository;
import org.transportsgt.operationalcostsservice.repositories.PagoChoferRepository;
import org.transportsgt.operationalcostsservice.repositories.RegistroDepreciacionRepository;
import org.transportsgt.operationalcostsservice.services.ResumenViajeService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResumenViajeServiceImpl implements ResumenViajeService {

    private final GastoCombustibleRepository combustibleRepository;
    private final RegistroDepreciacionRepository depreciacionRepository;
    private final PagoChoferRepository pagoChoferRepository;
    private final CostosMapper mapper;
    private final PoliticaAccesoSucursal politica;

    @Override
    @Transactional(readOnly = true)
    public ResumenCostosViajeResponse resumenViaje(UUID idViaje, UsuarioAutenticado usuario) {
        GastoCombustible comb = combustibleRepository.findByIdViaje(idViaje).orElse(null);
        RegistroDepreciacion dep = depreciacionRepository.findByIdViaje(idViaje).orElse(null);
        PagoChofer pago = pagoChoferRepository.findByIdViaje(idViaje).orElse(null);

        UUID sucursalConCostos = comb != null ? comb.getIdSucursal()
                : dep != null ? dep.getIdSucursal() : null;
        politica.exigirMismaSucursal(usuario, sucursalConCostos,
                "El viaje no tiene costos en su sucursal");

        if (comb == null && dep == null && pago == null) {
            throw new NoEncontradoException("VIAJE_SIN_COSTOS",
                    "El viaje " + idViaje + " no tiene costos registrados");
        }
        return new ResumenCostosViajeResponse(
                idViaje,
                comb != null ? mapper.toCombustibleResponse(comb) : null,
                dep != null ? mapper.toDepreciacionResponse(dep) : null,
                pago != null ? mapper.toPagoChoferResponse(pago) : null);
    }
}
