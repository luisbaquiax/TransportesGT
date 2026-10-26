package org.transportsgt.notificationservice.services.licencia.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.transportsgt.notificationservice.repositories.RecordatorioLicenciaRepository;
import org.transportsgt.notificationservice.services.licencia.AvisosLicenciaService;
import org.transportsgt.notificationservice.services.licencia.ResumenAvisos;
import org.transportsgt.notificationservice.utils.Fechas;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvisosLicenciaServiceImpl implements AvisosLicenciaService {

    private final RecordatorioLicenciaRepository recordatorioRepository;
    private final GestorAvisoLicencia gestor;
    private final Fechas fechas;

    @Override
    public ResumenAvisos procesar() {
        Map<GestorAvisoLicencia.Resultado, Integer> conteo = new EnumMap<>(GestorAvisoLicencia.Resultado.class);
        int conError = 0;
        for (UUID id : recordatorioRepository.findIdsPendientesHasta(fechas.hoy())) {
            try {
                conteo.merge(gestor.procesar(id), 1, Integer::sum);
            } catch (RuntimeException e) {
                conError++;
                log.error("No se pudo procesar el aviso de licencia {}", id, e);
            }
        }
        return new ResumenAvisos(conteo.getOrDefault(GestorAvisoLicencia.Resultado.ENVIADO, 0),
                conteo.getOrDefault(GestorAvisoLicencia.Resultado.CANCELADO, 0), conError);
    }
}
