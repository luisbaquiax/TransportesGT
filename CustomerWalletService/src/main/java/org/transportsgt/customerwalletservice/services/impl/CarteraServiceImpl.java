package org.transportsgt.customerwalletservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.customerwalletservice.config.security.UsuarioAutenticado;
import org.transportsgt.customerwalletservice.dto.request.PagoRequest;
import org.transportsgt.customerwalletservice.dto.request.RecargaRequest;
import org.transportsgt.customerwalletservice.dto.response.CarteraResponse;
import org.transportsgt.customerwalletservice.dto.response.MovimientoResponse;
import org.transportsgt.customerwalletservice.dto.response.PagoConsultaResponse;
import org.transportsgt.customerwalletservice.dto.response.PagoResponse;
import org.transportsgt.customerwalletservice.dto.response.PaginaResponse;
import org.transportsgt.customerwalletservice.dto.response.Resultado;
import org.transportsgt.customerwalletservice.eventos.EventosCartera;
import org.transportsgt.customerwalletservice.exception.ClaveIdempotenciaInvalidaException;
import org.transportsgt.customerwalletservice.exception.ClaveIdempotenciaRequeridaException;
import org.transportsgt.customerwalletservice.exception.ClaveIdempotenciaReutilizadaException;
import org.transportsgt.customerwalletservice.exception.ClienteNoEncontradoException;
import org.transportsgt.customerwalletservice.exception.CuentaInactivaException;
import org.transportsgt.customerwalletservice.exception.PagoNoEncontradoException;
import org.transportsgt.customerwalletservice.exception.PerfilIncompletoException;
import org.transportsgt.customerwalletservice.exception.RangoFechasInvalidoException;
import org.transportsgt.customerwalletservice.exception.SaldoInsuficienteException;
import org.transportsgt.customerwalletservice.mappers.CarteraMapper;
import org.transportsgt.customerwalletservice.models.Cartera;
import org.transportsgt.customerwalletservice.models.Cliente;
import org.transportsgt.customerwalletservice.models.MovimientoCartera;
import org.transportsgt.customerwalletservice.models.TipoMovimiento;
import org.transportsgt.customerwalletservice.repositories.CarteraRepository;
import org.transportsgt.customerwalletservice.repositories.MovimientoCarteraRepository;
import org.transportsgt.customerwalletservice.repositories.MovimientoCarteraSpecs;
import org.transportsgt.customerwalletservice.services.CarteraService;
import org.transportsgt.customerwalletservice.utils.Dinero;
import org.transportsgt.eventos.payload.TipoReferencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CarteraServiceImpl implements CarteraService {

    static final int LARGO_MAXIMO_CLAVE = 100;
    private static final Sort ORDEN_MOVIMIENTOS = Sort.by(Sort.Order.desc("fechaMovimiento"), Sort.Order.desc("creadoEn"));

    private final CarteraRepository carteraRepository;
    private final MovimientoCarteraRepository movimientoRepository;
    private final CarteraMapper carteraMapper;
    private final EventosCartera eventos;

    @Override
    public CarteraResponse obtener(UsuarioAutenticado usuario) {
        return carteraMapper.toResponse(buscarCartera(usuario.id()));
    }

    @Override
    @Transactional
    public Resultado<MovimientoResponse> recargar(UsuarioAutenticado usuario, String claveIdempotencia,
                                                  RecargaRequest request) {
        String clave = validarClave(claveIdempotencia);
        BigDecimal monto = Dinero.escalar(request.monto());
        // El bloqueo va en la primera lectura: así otra petición con la misma clave espera y luego ve la recarga
        Cartera bloqueada = bloquear(usuario.id());
        validarActiva(bloqueada.getCliente());

        Optional<MovimientoCartera> repetida = movimientoRepository.findByClaveIdempotencia(clave);
        if (repetida.isPresent()) {
            return Resultado.existente(carteraMapper.toResponse(
                    verificarRepetido(repetida.get(), TipoMovimiento.RECARGA, bloqueada, monto)));
        }
        BigDecimal saldoPosterior = Dinero.escalar(bloqueada.getSaldo().add(monto));
        MovimientoCartera recarga = movimientoRepository.save(MovimientoCartera.builder()
                .cartera(bloqueada)
                .tipo(TipoMovimiento.RECARGA)
                .monto(monto)
                .saldoPosterior(saldoPosterior)
                .claveIdempotencia(clave)
                .fechaMovimiento(request.fechaRecarga())
                .descripcion("Recarga de saldo")
                .build());
        bloqueada.setSaldo(saldoPosterior);
        Cartera guardada = carteraRepository.saveAndFlush(bloqueada);
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
                        MovimientoCarteraSpecs.filtrar(cartera.getId(), tipo, desde, hasta),
                        PageRequest.of(pagina, tamano, ORDEN_MOVIMIENTOS)),
                carteraMapper::toResponse);
    }

    @Override
    @Transactional
    public Resultado<PagoResponse> pagar(UsuarioAutenticado usuario, String claveIdempotencia, PagoRequest request) {
        String clave = validarClave(claveIdempotencia);
        if (!clave.equalsIgnoreCase(request.idReferencia().toString())) {
            throw new ClaveIdempotenciaInvalidaException();
        }
        BigDecimal monto = Dinero.escalar(request.monto());
        // El bloqueo va en la primera lectura: el saldo que se valida es el vigente, no una copia en caché
        Cartera bloqueada = bloquear(usuario.id());
        Cliente cliente = bloqueada.getCliente();
        validarActiva(cliente);
        if (cliente.getNit() == null) {
            throw new PerfilIncompletoException();
        }

        Optional<MovimientoCartera> existente = buscarPago(request.tipoReferencia(), request.idReferencia());
        if (existente.isPresent()) {
            return Resultado.existente(carteraMapper.toPago(
                    verificarRepetido(existente.get(), TipoMovimiento.PAGO, bloqueada, monto)));
        }
        if (bloqueada.getSaldo().compareTo(monto) < 0) {
            throw new SaldoInsuficienteException();
        }
        BigDecimal saldoPosterior = Dinero.escalar(bloqueada.getSaldo().subtract(monto));
        MovimientoCartera pago = movimientoRepository.save(MovimientoCartera.builder()
                .cartera(bloqueada)
                .tipo(TipoMovimiento.PAGO)
                .monto(monto)
                .saldoPosterior(saldoPosterior)
                .tipoReferencia(request.tipoReferencia())
                .idReferencia(request.idReferencia())
                .claveIdempotencia(clave)
                .fechaMovimiento(request.fechaPago())
                .descripcion(request.descripcion())
                .build());
        bloqueada.setSaldo(saldoPosterior);
        carteraRepository.saveAndFlush(bloqueada);
        return Resultado.nuevo(carteraMapper.toPago(pago));
    }

    @Override
    public PagoConsultaResponse consultarPago(UUID idUsuario, TipoReferencia tipoReferencia, UUID idReferencia) {
        MovimientoCartera pago = buscarPago(tipoReferencia, idReferencia).orElseThrow(PagoNoEncontradoException::new);
        if (idUsuario != null && !idUsuario.equals(pago.getCartera().getCliente().getIdUsuario())) {
            // No revela que la referencia existe en otra cartera
            throw new PagoNoEncontradoException();
        }
        return carteraMapper.toConsulta(pago);
    }

    private Cartera buscarCartera(UUID idUsuario) {
        return carteraRepository.findByClienteIdUsuario(idUsuario).orElseThrow(ClienteNoEncontradoException::new);
    }

    /**
     * {@code SELECT ... FOR UPDATE}: serializa las recargas y pagos de la misma cartera.
     */
    private Cartera bloquear(UUID idUsuario) {
        return carteraRepository.findByIdUsuarioParaActualizar(idUsuario).orElseThrow(ClienteNoEncontradoException::new);
    }

    private Optional<MovimientoCartera> buscarPago(TipoReferencia tipoReferencia, UUID idReferencia) {
        return movimientoRepository.findByTipoAndTipoReferenciaAndIdReferencia(
                TipoMovimiento.PAGO, tipoReferencia, idReferencia);
    }

    private static String validarClave(String clave) {
        if (clave == null || clave.isBlank()) {
            throw new ClaveIdempotenciaRequeridaException();
        }
        String limpia = clave.trim();
        if (limpia.length() > LARGO_MAXIMO_CLAVE) {
            throw new ClaveIdempotenciaRequeridaException();
        }
        return limpia;
    }

    private static void validarActiva(Cliente cliente) {
        if (!Boolean.TRUE.equals(cliente.getActivo())) {
            throw new CuentaInactivaException();
        }
    }

    /**
     * Operación repetida: solo es válida si es del mismo tipo, con la misma cartera y el mismo monto.
     */
    private static MovimientoCartera verificarRepetido(MovimientoCartera original, TipoMovimiento tipo,
                                                       Cartera cartera, BigDecimal monto) {
        if (original.getTipo() != tipo || !original.getCartera().getId().equals(cartera.getId())
                || !Dinero.iguales(original.getMonto(), monto)) {
            throw new ClaveIdempotenciaReutilizadaException();
        }
        return original;
    }
}
