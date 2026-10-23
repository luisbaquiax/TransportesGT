package org.transportsgt.privaterentalservice.services.solicitud.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.privaterentalservice.dto.request.SolicitudAlquilerRequest;
import org.transportsgt.privaterentalservice.exception.ReglaNegocioException;
import org.transportsgt.privaterentalservice.exception.SolicitudInvalidaException;
import org.transportsgt.privaterentalservice.models.SucursalRef;
import org.transportsgt.privaterentalservice.repositories.SucursalRefRepository;
import org.transportsgt.privaterentalservice.services.comun.CalendarioGuatemala;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Reglas de la solicitud que Bean Validation no expresa (fechas, coordenadas y sucursal).
 */
@Component
@RequiredArgsConstructor
class ValidadorSolicitud {

    private static final BigDecimal LATITUD_MAX = BigDecimal.valueOf(90);
    private static final BigDecimal LONGITUD_MAX = BigDecimal.valueOf(180);

    private final SucursalRefRepository sucursalRepository;
    private final CalendarioGuatemala calendario;

    void validar(SolicitudAlquilerRequest req) {
        validarFechas(req);
        validarCoordenadas(req);
    }

    void validarSucursal(UUID idSucursal) {
        SucursalRef s = sucursalRepository.findById(idSucursal)
                .orElseThrow(() -> new SolicitudInvalidaException("No existe la sucursal " + idSucursal));
        if (!Boolean.TRUE.equals(s.getActiva())) {
            throw new SolicitudInvalidaException("La sucursal de origen está inactiva");
        }
    }

    private void validarFechas(SolicitudAlquilerRequest req) {
        if (req.esIdaYVuelta() && req.fechaHoraRetorno() == null) {
            throw new SolicitudInvalidaException("Un alquiler ida y vuelta requiere fechaHoraRetorno");
        }
        if (!req.esIdaYVuelta() && req.fechaHoraRetorno() != null) {
            throw new SolicitudInvalidaException("fechaHoraRetorno solo aplica a ida y vuelta");
        }
        if (req.fechaHoraRetorno() != null && !req.fechaHoraRetorno().isAfter(req.fechaHoraSalida())) {
            throw new SolicitudInvalidaException("La fecha de retorno debe ser posterior a la salida");
        }
        if (req.fechaHoraSalida().isBefore(calendario.ahora())) {
            throw new SolicitudInvalidaException("La fecha de salida ya pasó");
        }
    }

    /**
     * Las cuatro juntas o ninguna (ck_alquiler_coordenadas) y dentro de rango: 422 en vez del error de la base.
     */
    private void validarCoordenadas(SolicitudAlquilerRequest req) {
        int presentes = contar(req.latitudOrigen()) + contar(req.longitudOrigen())
                + contar(req.latitudDestino()) + contar(req.longitudDestino());
        if (presentes == 0) {
            return;
        }
        if (presentes < 4) {
            throw new ReglaNegocioException("COORDENADAS_INCOMPLETAS",
                    "Indique las 4 coordenadas (origen y destino) o ninguna");
        }
        if (fueraDeRango(req.latitudOrigen(), LATITUD_MAX) || fueraDeRango(req.latitudDestino(), LATITUD_MAX)) {
            throw new ReglaNegocioException("COORDENADAS_INVALIDAS", "La latitud debe estar entre -90 y 90");
        }
        if (fueraDeRango(req.longitudOrigen(), LONGITUD_MAX) || fueraDeRango(req.longitudDestino(), LONGITUD_MAX)) {
            throw new ReglaNegocioException("COORDENADAS_INVALIDAS", "La longitud debe estar entre -180 y 180");
        }
    }

    private int contar(BigDecimal valor) {
        return valor == null ? 0 : 1;
    }

    private boolean fueraDeRango(BigDecimal valor, BigDecimal limite) {
        return valor.abs().compareTo(limite) > 0;
    }
}
