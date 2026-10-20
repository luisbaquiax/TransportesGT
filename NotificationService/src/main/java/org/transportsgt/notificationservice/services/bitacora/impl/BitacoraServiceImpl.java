package org.transportsgt.notificationservice.services.bitacora.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.notificationservice.dto.response.NotificacionDetalleResponse;
import org.transportsgt.notificationservice.dto.response.NotificacionResumenResponse;
import org.transportsgt.notificationservice.dto.response.PaginaResponse;
import org.transportsgt.notificationservice.exception.NotificacionNoEncontradaException;
import org.transportsgt.notificationservice.exception.RangoFechasInvalidoException;
import org.transportsgt.notificationservice.exception.ReintentoNoPermitidoException;
import org.transportsgt.notificationservice.mappers.NotificacionMapper;
import org.transportsgt.notificationservice.models.EstadoNotificacion;
import org.transportsgt.notificationservice.models.Notificacion;
import org.transportsgt.notificationservice.models.TipoNotificacion;
import org.transportsgt.notificationservice.repositories.NotificacionRepository;
import org.transportsgt.notificationservice.repositories.NotificacionSpecs;
import org.transportsgt.notificationservice.services.bitacora.BitacoraService;
import org.transportsgt.notificationservice.utils.Fechas;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BitacoraServiceImpl implements BitacoraService {

    private final NotificacionRepository notificacionRepository;
    private final NotificacionSpecs specs;
    private final NotificacionMapper mapper;
    private final Fechas fechas;

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<NotificacionResumenResponse> listar(EstadoNotificacion estado, TipoNotificacion tipo,
                                                              LocalDate desde, LocalDate hasta, int pagina, int tamano) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new RangoFechasInvalidoException();
        }
        return PaginaResponse.de(notificacionRepository.findAll(
                specs.filtrar(estado, tipo, desde == null ? null : fechas.inicioDelDia(desde),
                        hasta == null ? null : fechas.inicioDelDia(hasta.plusDays(1))),
                PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "creadoEn"))), mapper::toResumen);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificacionDetalleResponse detalle(UUID idNotificacion) {
        return mapper.toDetalle(buscar(idNotificacion));
    }

    @Override
    @Transactional
    public NotificacionResumenResponse reintentar(UUID idNotificacion) {
        Notificacion notificacion = buscar(idNotificacion);
        if (notificacion.getTipo() == TipoNotificacion.ENLACE_CONTRASENA) {
            throw new ReintentoNoPermitidoException(
                    "Un enlace de contraseña no se reenvía: el usuario debe solicitar uno nuevo");
        }
        if (notificacion.getEstado() != EstadoNotificacion.FALLIDA) {
            throw new ReintentoNoPermitidoException(
                    "Solo se reintenta un correo FALLIDA (estado actual: " + notificacion.getEstado() + ")");
        }
        notificacion.setEstado(EstadoNotificacion.PENDIENTE);
        notificacion.setIntentos((short) 0);
        notificacion.setProximoIntentoEn(fechas.ahora());
        return mapper.toResumen(notificacionRepository.save(notificacion));
    }

    private Notificacion buscar(UUID idNotificacion) {
        return notificacionRepository.findById(idNotificacion).orElseThrow(NotificacionNoEncontradaException::new);
    }
}
