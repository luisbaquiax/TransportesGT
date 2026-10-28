package org.transportsgt.customerwalletservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.customerwalletservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.customerwalletservice.dto.request.PagoRequest;
import org.transportsgt.customerwalletservice.dto.response.PagoConsultaResponse;
import org.transportsgt.customerwalletservice.dto.response.PagoResponse;
import org.transportsgt.customerwalletservice.dto.response.Resultado;
import org.transportsgt.customerwalletservice.exception.ClaveIdempotenciaInvalidaException;
import org.transportsgt.customerwalletservice.exception.ClienteNoEncontradoException;
import org.transportsgt.customerwalletservice.exception.PagoNoEncontradoException;
import org.transportsgt.customerwalletservice.exception.PerfilIncompletoException;
import org.transportsgt.customerwalletservice.mappers.CarteraMapper;
import org.transportsgt.customerwalletservice.models.Cartera;
import org.transportsgt.customerwalletservice.models.Cliente;
import org.transportsgt.customerwalletservice.models.MovimientoCartera;
import org.transportsgt.customerwalletservice.models.TipoMovimiento;
import org.transportsgt.customerwalletservice.repositories.CarteraRepository;
import org.transportsgt.customerwalletservice.repositories.MovimientoCarteraRepository;
import org.transportsgt.customerwalletservice.services.PagoService;
import org.transportsgt.customerwalletservice.utils.Dinero;
import org.transportsgt.eventos.payload.costos.TipoReferencia;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PagoServiceImpl implements PagoService {

    private final CarteraRepository carteraRepository;
    private final MovimientoCarteraRepository movimientoRepository;
    private final CarteraMapper carteraMapper;
    private final ReglasCartera reglas;
    private final Dinero dinero;

    @Override
    @Transactional
    public Resultado<PagoResponse> pagar(UsuarioAutenticado usuario, String claveIdempotencia, PagoRequest request) {
        String clave = reglas.validarClave(claveIdempotencia);
        if (!clave.equalsIgnoreCase(request.idReferencia().toString())) {
            throw new ClaveIdempotenciaInvalidaException();
        }
        BigDecimal monto = dinero.escalar(request.monto());
        // El bloqueo va en la primera lectura: el saldo que se valida es el vigente, no una copia en caché
        Cartera cartera = carteraRepository.findByIdUsuarioParaActualizar(usuario.id())
                .orElseThrow(ClienteNoEncontradoException::new);
        Cliente cliente = cartera.getCliente();
        reglas.validarActiva(cliente);
        if (cliente.getNit() == null) {
            throw new PerfilIncompletoException();
        }

        Optional<MovimientoCartera> existente = buscarPago(request.tipoReferencia(), request.idReferencia());
        if (existente.isPresent()) {
            return Resultado.existente(carteraMapper.toPago(
                    reglas.verificarRepetido(existente.get(), TipoMovimiento.PAGO, cartera, monto)));
        }
        BigDecimal saldoPosterior = reglas.debitar(cartera, monto);
        MovimientoCartera pago = movimientoRepository.save(MovimientoCartera.builder()
                .cartera(cartera)
                .tipo(TipoMovimiento.PAGO)
                .monto(monto)
                .saldoPosterior(saldoPosterior)
                .tipoReferencia(request.tipoReferencia())
                .idReferencia(request.idReferencia())
                .claveIdempotencia(clave)
                .fechaMovimiento(request.fechaPago())
                .descripcion(request.descripcion())
                .build());
        carteraRepository.saveAndFlush(cartera);
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

    private Optional<MovimientoCartera> buscarPago(TipoReferencia tipoReferencia, UUID idReferencia) {
        return movimientoRepository.findByTipoAndTipoReferenciaAndIdReferencia(
                TipoMovimiento.PAGO, tipoReferencia, idReferencia);
    }
}
