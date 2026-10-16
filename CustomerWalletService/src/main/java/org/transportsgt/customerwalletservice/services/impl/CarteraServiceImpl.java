package org.transportsgt.customerwalletservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.customerwalletservice.config.security.UsuarioAutenticado;
import org.transportsgt.customerwalletservice.dto.request.RecargaRequest;
import org.transportsgt.customerwalletservice.dto.response.CarteraResponse;
import org.transportsgt.customerwalletservice.dto.response.MovimientoResponse;
import org.transportsgt.customerwalletservice.dto.response.PaginaResponse;
import org.transportsgt.customerwalletservice.dto.response.Resultado;
import org.transportsgt.customerwalletservice.eventos.EventosCartera;
import org.transportsgt.customerwalletservice.exception.ClienteNoEncontradoException;
import org.transportsgt.customerwalletservice.exception.RangoFechasInvalidoException;
import org.transportsgt.customerwalletservice.mappers.CarteraMapper;
import org.transportsgt.customerwalletservice.models.Cartera;
import org.transportsgt.customerwalletservice.models.MovimientoCartera;
import org.transportsgt.customerwalletservice.models.TipoMovimiento;
import org.transportsgt.customerwalletservice.repositories.CarteraRepository;
import org.transportsgt.customerwalletservice.repositories.MovimientoCarteraRepository;
import org.transportsgt.customerwalletservice.repositories.MovimientoCarteraSpecs;
import org.transportsgt.customerwalletservice.services.CarteraService;
import org.transportsgt.customerwalletservice.utils.Dinero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CarteraServiceImpl implements CarteraService {

    private static final Sort ORDEN_MOVIMIENTOS = Sort.by(Sort.Order.desc("fechaMovimiento"), Sort.Order.desc("creadoEn"));

    private final CarteraRepository carteraRepository;
    private final MovimientoCarteraRepository movimientoRepository;
    private final CarteraMapper carteraMapper;
    private final EventosCartera eventos;
    private final ReglasCartera reglas;
    private final MovimientoCarteraSpecs specs;
    private final Dinero dinero;

    @Override
    public CarteraResponse obtener(UsuarioAutenticado usuario) {
        return carteraMapper.toResponse(buscarCartera(usuario.id()));
    }

    @Override
    @Transactional
    public Resultado<MovimientoResponse> recargar(UsuarioAutenticado usuario, String claveIdempotencia,
                                                  RecargaRequest request) {
        String clave = reglas.validarClave(claveIdempotencia);
        BigDecimal monto = dinero.escalar(request.monto());
        // El bloqueo va en la primera lectura: así otra petición con la misma clave espera y luego ve la recarga
        Cartera cartera = carteraRepository.findByIdUsuarioParaActualizar(usuario.id())
                .orElseThrow(ClienteNoEncontradoException::new);
        reglas.validarActiva(cartera.getCliente());

        Optional<MovimientoCartera> repetida = movimientoRepository.findByClaveIdempotencia(clave);
        if (repetida.isPresent()) {
            return Resultado.existente(carteraMapper.toResponse(
                    reglas.verificarRepetido(repetida.get(), TipoMovimiento.RECARGA, cartera, monto)));
        }
        BigDecimal saldoPosterior = reglas.acreditar(cartera, monto);
        MovimientoCartera recarga = movimientoRepository.save(MovimientoCartera.builder()
                .cartera(cartera)
                .tipo(TipoMovimiento.RECARGA)
                .monto(monto)
                .saldoPosterior(saldoPosterior)
                .claveIdempotencia(clave)
                .fechaMovimiento(request.fechaRecarga())
                .descripcion("Recarga de saldo")
                .build());
        Cartera guardada = carteraRepository.saveAndFlush(cartera);
        eventos.carteraRecargada(guardada, recarga);
        return Resultado.nuevo(carteraMapper.toResponse(recarga));
    }

    @Override
    public PaginaResponse<MovimientoResponse> movimientos(UsuarioAutenticado usuario, TipoMovimiento tipo,
                                                          LocalDate desde, LocalDate hasta, int pagina, int tamano) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new RangoFechasInvalidoException();
        }
        Cartera cartera = buscarCartera(usuario.id());
        return PaginaResponse.de(movimientoRepository.findAll(
                        specs.filtrar(cartera.getId(), tipo, desde, hasta),
                        PageRequest.of(pagina, tamano, ORDEN_MOVIMIENTOS)),
                carteraMapper::toResponse);
    }

    private Cartera buscarCartera(UUID idUsuario) {
        return carteraRepository.findByClienteIdUsuario(idUsuario).orElseThrow(ClienteNoEncontradoException::new);
    }
}
