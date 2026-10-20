package org.transportsgt.notificationservice.services.licencia.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.notificationservice.models.ChoferLicencia;
import org.transportsgt.notificationservice.models.EstadoRecordatorio;
import org.transportsgt.notificationservice.models.RecordatorioLicencia;
import org.transportsgt.notificationservice.models.TipoNotificacion;
import org.transportsgt.notificationservice.repositories.RecordatorioLicenciaRepository;
import org.transportsgt.notificationservice.services.bandeja.BandejaSalida;
import org.transportsgt.notificationservice.services.bandeja.ClaveDedup;
import org.transportsgt.notificationservice.services.bandeja.Destinatario;
import org.transportsgt.notificationservice.services.bandeja.Destinatarios;
import org.transportsgt.notificationservice.services.bandeja.SolicitudCorreo;
import org.transportsgt.notificationservice.utils.Fechas;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * Un aviso por transacción: sus correos y el cambio de estado se confirman juntos. Bean aparte de
 * {@link AvisosLicenciaServiceImpl} para que el proxy de {@code @Transactional} aplique.
 */
@Component
@RequiredArgsConstructor
class GestorAvisoLicencia {

    enum Resultado {ENVIADO, CANCELADO, OMITIDO}

    private final RecordatorioLicenciaRepository recordatorioRepository;
    private final Destinatarios destinatarios;
    private final BandejaSalida bandeja;
    private final ClaveDedup claveDedup;
    private final Fechas fechas;

    @Transactional
    public Resultado procesar(UUID idRecordatorio) {
        RecordatorioLicencia aviso = recordatorioRepository.findByIdParaActualizar(idRecordatorio).orElse(null);
        if (aviso == null || aviso.getEstado() != EstadoRecordatorio.PENDIENTE) {
            // Otra instancia lo procesó, o un ChoferActualizado lo canceló, mientras tanto
            return Resultado.OMITIDO;
        }
        ChoferLicencia chofer = aviso.getChofer();
        if (!chofer.getActivo() || !chofer.getFechaVencimientoLicencia().equals(aviso.getFechaVencimiento())) {
            aviso.setEstado(EstadoRecordatorio.CANCELADO);
            return Resultado.CANCELADO;
        }
        LocalDate hoy = fechas.hoy();
        // Días reales: si el job se atrasó, el correo no dice "30 días" cuando faltan menos
        long dias = Math.max(0, ChronoUnit.DAYS.between(hoy, aviso.getFechaVencimiento()));
        encolar(aviso, new Destinatario(
                null,
                chofer.getCorreo(),
                chofer.getNombreCompleto()),
                chofer,
                dias,
                false);
        for (Destinatario admin : destinatarios.adminsDeSucursal(chofer.getIdSucursal())) {
            encolar(aviso, admin, chofer, dias, true);
        }
        aviso.setEstado(EstadoRecordatorio.ENVIADO);
        aviso.setEnviadoEn(fechas.ahora());
        aviso.setIntentos((short) (aviso.getIntentos() + 1));
        return Resultado.ENVIADO;
    }

    private void encolar(RecordatorioLicencia aviso, Destinatario destinatario, ChoferLicencia chofer, long dias,
                         boolean paraAdmin) {
        bandeja.encolar(new SolicitudCorreo(TipoNotificacion.LICENCIA_POR_VENCER, destinatario,
                Map.of("chofer", chofer, "dias", dias, "fechaVencimiento", aviso.getFechaVencimiento(),
                        "paraAdmin", paraAdmin),
                claveDedup.licencia(aviso.getId(), destinatario.correo()), null));
    }
}
