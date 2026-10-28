package org.transportsgt.customerwalletservice.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.customerwalletservice.eventos.EventosCartera;
import org.transportsgt.customerwalletservice.models.Cartera;
import org.transportsgt.customerwalletservice.models.MovimientoCartera;
import org.transportsgt.customerwalletservice.models.TipoMovimiento;
import org.transportsgt.customerwalletservice.repositories.CarteraRepository;
import org.transportsgt.customerwalletservice.repositories.MovimientoCarteraRepository;
import org.transportsgt.customerwalletservice.services.ReembolsoService;
import org.transportsgt.eventos.mensajeria.excepciones.EventoNoRecuperableException;
import org.transportsgt.eventos.payload.costos.TipoReferencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReembolsoServiceImpl implements ReembolsoService {

    static final String MOTIVO_POR_DEFECTO = "VIAJE_ELIMINADO";

    private final CarteraRepository carteraRepository;
    private final MovimientoCarteraRepository movimientoRepository;
    private final EventosCartera eventos;
    private final ReglasCartera reglas;

    @Override
    @Transactional
    public boolean reembolsar(TipoReferencia tipoReferencia,
                              UUID idReferencia,
                              UUID idPago,
                              LocalDate fechaMovimiento,
                              String motivo) {
        Optional<MovimientoCartera> pago = buscarPago(tipoReferencia, idReferencia, idPago);
        if (pago.isEmpty()) {
            log.info("Sin PAGO para {} {}: no hay nada que reembolsar", tipoReferencia, idReferencia);
            return false;
        }
        // Se bloquea antes de revisar: un reembolso simultáneo de la misma referencia espera y luego lo ve
        Cartera cartera = carteraRepository.findByIdParaActualizar(pago.get().getCartera().getId())
                .orElseThrow(() -> new EventoNoRecuperableException("No existe la cartera del pago " + pago.get().getId()));
        if (yaReembolsado(tipoReferencia, idReferencia)) {
            return false;
        }
        BigDecimal monto = pago.get().getMonto();
        BigDecimal saldoPosterior = reglas.acreditar(cartera, monto);
        String motivoFinal = motivo == null || motivo.isBlank() ? MOTIVO_POR_DEFECTO : motivo;
        MovimientoCartera reembolso = movimientoRepository.save(MovimientoCartera.builder()
                .cartera(cartera)
                .tipo(TipoMovimiento.REEMBOLSO)
                .monto(monto)
                .saldoPosterior(saldoPosterior)
                .tipoReferencia(tipoReferencia)
                .idReferencia(idReferencia)
                .fechaMovimiento(fechaMovimiento)
                .descripcion("Reembolso: " + motivoFinal)
                .build());
        Cartera guardada = carteraRepository.saveAndFlush(cartera);
        eventos.carteraReembolsada(guardada, reembolso, motivoFinal);
        return true;
    }

    private Optional<MovimientoCartera> buscarPago(TipoReferencia tipoReferencia, UUID idReferencia, UUID idPago) {
        if (idPago != null) {
            Optional<MovimientoCartera> porId = movimientoRepository.findByIdAndTipo(idPago, TipoMovimiento.PAGO);
            if (porId.isPresent()) {
                MovimientoCartera pago = porId.get();
                if (pago.getTipoReferencia() != tipoReferencia || !idReferencia.equals(pago.getIdReferencia())) {
                    throw new EventoNoRecuperableException("El pago " + idPago + " no corresponde a "
                            + tipoReferencia + " " + idReferencia);
                }
                return porId;
            }
        }
        return movimientoRepository.findByTipoAndTipoReferenciaAndIdReferencia(
                TipoMovimiento.PAGO, tipoReferencia, idReferencia);
    }

    private boolean yaReembolsado(TipoReferencia tipoReferencia, UUID idReferencia) {
        return movimientoRepository.findByTipoAndTipoReferenciaAndIdReferencia(
                TipoMovimiento.REEMBOLSO, tipoReferencia, idReferencia).isPresent();
    }
}
