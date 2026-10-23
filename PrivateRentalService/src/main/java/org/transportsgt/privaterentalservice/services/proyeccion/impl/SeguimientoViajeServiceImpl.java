package org.transportsgt.privaterentalservice.services.proyeccion.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.DatosLlegada;
import org.transportsgt.eventos.payload.DatosViaje;
import org.transportsgt.eventos.payload.DatosViajeEliminado;
import org.transportsgt.privaterentalservice.eventos.EventosAlquiler;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;
import org.transportsgt.privaterentalservice.repositories.AlquilerRepository;
import org.transportsgt.privaterentalservice.services.comun.CalendarioGuatemala;
import org.transportsgt.privaterentalservice.services.comun.GuardadoAlquiler;
import org.transportsgt.privaterentalservice.services.comun.TransicionesAlquiler;
import org.transportsgt.privaterentalservice.services.proyeccion.SeguimientoViajeService;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeguimientoViajeServiceImpl implements SeguimientoViajeService {

    static final String MOTIVO_VIAJE_ELIMINADO = "VIAJE_ELIMINADO";

    /**
     * Solo se cancela un alquiler que llegó a tener viaje (o estaba por tenerlo).
     */
    private static final Set<EstadoAlquiler> CANCELABLES_POR_VIAJE =
            EnumSet.of(EstadoAlquiler.COTIZADO, EstadoAlquiler.CONFIRMADO);

    private final AlquilerRepository alquilerRepository;
    private final TransicionesAlquiler transiciones;
    private final GuardadoAlquiler guardado;
    private final CalendarioGuatemala calendario;
    private final EventosAlquiler eventos;

    @Override
    @Transactional
    public void enlazarViaje(DatosViaje d, Long versionAgregado) {
        aplicarInstantanea(d, versionAgregado);
    }

    @Override
    @Transactional
    public void actualizarRecursos(DatosViaje d, Long versionAgregado) {
        aplicarInstantanea(d, versionAgregado);
    }

    @Override
    @Transactional
    public void cancelarPorViajeEliminado(DatosViajeEliminado d) {
        conAlquiler(d.idAlquiler(), a -> {
            if (!CANCELABLES_POR_VIAJE.contains(a.getEstado())) {
                log.debug("Alquiler {} en estado {} no se cancela por viaje eliminado", a.getId(), a.getEstado());
                return;
            }
            transiciones.aplicar(a, EstadoAlquiler.CANCELADO, "cancelar por viaje eliminado");
            a.setMotivoCancelacion(MOTIVO_VIAJE_ELIMINADO);
            a.setCanceladoEn(calendario.ahora());
            Alquiler cancelado = guardado.guardar(a);
            eventos.alquilerCancelado(cancelado, MOTIVO_VIAJE_ELIMINADO);
            log.info("Alquiler {} cancelado por eliminación del viaje {}", a.getId(), d.idViaje());
        });
    }

    @Override
    @Transactional
    public void finalizarPorLlegada(DatosLlegada d) {
        conAlquiler(d.idAlquiler(), a -> {
            if (a.getEstado() != EstadoAlquiler.CONFIRMADO) {
                log.debug("Alquiler {} en estado {} no se finaliza", a.getId(), a.getEstado());
                return;
            }
            transiciones.aplicar(a, EstadoAlquiler.FINALIZADO, "finalizar");
            guardado.guardar(a);
            log.info("Alquiler {} finalizado tras la llegada del viaje {}", a.getId(), d.idViaje());
        });
    }

    /**
     * ViajeProgramado y ViajeActualizado traen la instantánea completa del viaje: se aplica solo si su versión es
     * mayor que la última aplicada, para que un evento atrasado no pise el bus o el chofer más recientes.
     */
    private void aplicarInstantanea(DatosViaje d, Long versionAgregado) {
        conAlquiler(d.idAlquiler(), a -> {
            if (versionAgregado != null && a.getVersionViaje() != null && versionAgregado <= a.getVersionViaje()) {
                log.debug("Viaje {} v{} ignorado: el alquiler {} ya tiene la v{}",
                        d.idViaje(), versionAgregado, a.getId(), a.getVersionViaje());
                return;
            }
            if (a.getIdViaje() == null) {
                a.setIdViaje(d.idViaje());
            }
            if (d.bus() != null) {
                a.setIdBus(d.bus().idBus());
                a.setPlacaBus(d.bus().placa());
            }
            if (d.chofer() != null) {
                a.setIdChofer(d.chofer().idChofer());
                a.setNombreChofer(d.chofer().nombreCompleto());
            }
            if (versionAgregado != null) {
                a.setVersionViaje(versionAgregado);
            }
            guardado.guardar(a);
            log.debug("Alquiler {} actualizado con el viaje {} (v{})", a.getId(), d.idViaje(), versionAgregado);
        });
    }

    private void conAlquiler(UUID idAlquiler, Consumer<Alquiler> accion) {
        if (idAlquiler == null) {
            return;
        }
        alquilerRepository.findById(idAlquiler).ifPresent(accion);
    }
}
