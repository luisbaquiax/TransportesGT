package org.transportsgt.notificationservice.services.proyeccion.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.DatosChofer;
import org.transportsgt.notificationservice.repositories.ChoferLicenciaRepository;
import org.transportsgt.notificationservice.repositories.RecordatorioLicenciaRepository;
import org.transportsgt.notificationservice.services.proyeccion.ProyeccionChoferesService;
import org.transportsgt.notificationservice.utils.Fechas;

import java.time.LocalDate;

/**
 * Se ejecuta en la transacción del consumidor: el chofer, sus avisos y {@code evento_procesado} se confirman juntos.
 */
@Service
@RequiredArgsConstructor
public class ProyeccionChoferesServiceImpl implements ProyeccionChoferesService {

    private final ChoferLicenciaRepository choferRepository;
    private final RecordatorioLicenciaRepository recordatorioRepository;
    private final CalendarioRecordatorios calendario;
    private final Fechas fechas;

    @Override
    @Transactional
    public boolean aplicar(DatosChofer datos, Long versionAgregado) {
        int aplicado = choferRepository.upsert(datos.idChofer(), datos.idSucursal(), datos.nombreCompleto(),
                datos.correo(), datos.fechaVencimientoLicencia(), datos.activo(),
                versionAgregado == null ? 0L : versionAgregado);
        if (aplicado == 0) {
            // Versión vieja o repetida: los avisos ya corresponden a una versión más nueva
            return false;
        }
        if (!datos.activo()) {
            recordatorioRepository.cancelarPendientes(datos.idChofer());
            return true;
        }
        LocalDate vencimiento = datos.fechaVencimientoLicencia();
        LocalDate hoy = fechas.hoy();
        recordatorioRepository.cancelarPendientesDeOtraFecha(datos.idChofer(), vencimiento);
        // Un chofer reactivado (o que vuelve a una fecha anterior) recupera los avisos cancelados que aún no pasaron
        recordatorioRepository.reactivarCancelados(datos.idChofer(), vencimiento, hoy);
        for (CalendarioRecordatorios.Aviso aviso : calendario.avisos(vencimiento, hoy)) {
            recordatorioRepository.programar(datos.idChofer(), vencimiento, aviso.diasAnticipacion(), aviso.fechaEnvio());
        }
        return true;
    }
}
