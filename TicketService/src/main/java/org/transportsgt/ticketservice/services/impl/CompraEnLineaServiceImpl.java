package org.transportsgt.ticketservice.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.transportsgt.ticketservice.clients.PasarelaPago;
import org.transportsgt.ticketservice.config.security.UsuarioAutenticado;
import org.transportsgt.ticketservice.dto.request.CompraRequest;
import org.transportsgt.ticketservice.dto.response.ResultadoCompra;
import org.transportsgt.ticketservice.exception.CarteraNoDisponibleException;
import org.transportsgt.ticketservice.exception.CompraAnuladaException;
import org.transportsgt.ticketservice.exception.PagoRechazadoException;
import org.transportsgt.ticketservice.exception.ViajeNoDisponibleException;
import org.transportsgt.ticketservice.mappers.CompraMapper;
import org.transportsgt.ticketservice.models.ClienteRef;
import org.transportsgt.ticketservice.models.Compra;
import org.transportsgt.ticketservice.models.EstadoCompra;
import org.transportsgt.ticketservice.models.ViajeRef;
import org.transportsgt.ticketservice.repositories.CompraRepository;
import org.transportsgt.ticketservice.services.CompraEnLineaService;
import org.transportsgt.ticketservice.utils.Dinero;
import org.transportsgt.eventos.payload.Canal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orquesta la saga. NO es transaccional a propósito: el cobro en Cartera ocurre entre dos transacciones de
 * {@link GestorCompras}, para no tener una conexión a la base abierta mientras se espera a otro servicio.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompraEnLineaServiceImpl implements CompraEnLineaService {

    private final CompraRepository compraRepository;
    private final ValidadorCompra validador;
    private final GestorCompras gestor;
    private final PasarelaPago pasarelaPago;
    private final CompraMapper compraMapper;
    private final Dinero dinero;

    @Override
    public ResultadoCompra comprar(UsuarioAutenticado comprador, String jwtComprador, CompraRequest request) {
        Optional<Compra> existente = compraRepository.findConBoletosById(request.idCompra());
        if (existente.isPresent()) {
            return continuar(existente.get(), comprador, jwtComprador, request);
        }
        Compra reservada;
        try {
            reservada = reservar(comprador, request);
        } catch (DataIntegrityViolationException e) {
            if (!String.valueOf(e.getMostSpecificCause().getMessage()).contains("compra_pkey")) {
                throw e;
            }
            // Otra petición con el mismo idCompra la creó al mismo tiempo: se trata como repetida
            Compra ganadora = compraRepository.findConBoletosById(request.idCompra()).orElseThrow(() -> e);
            return continuar(ganadora, comprador, jwtComprador, request);
        }
        return cobrarYConfirmar(reservada, jwtComprador);
    }

    /**
     * {@code idCompra} repetido: confirmada → la misma respuesta; reservada → se reintenta el cobro (Cartera es
     * idempotente con la misma clave); anulada → 409.
     */
    private ResultadoCompra continuar(Compra existente, UsuarioAutenticado comprador, String jwtComprador,
                                      CompraRequest request) {
        validador.verificarRepetida(existente, Canal.EN_LINEA, comprador.id(), request.idViaje(), request.asientos());
        if (existente.getEstado() == EstadoCompra.CONFIRMADA) {
            return new ResultadoCompra(compraMapper.toResponse(existente), ResultadoCompra.Tipo.EXISTENTE);
        }
        if (existente.getEstado() == EstadoCompra.ANULADA) {
            throw new CompraAnuladaException(existente.getMotivoAnulacion());
        }
        return cobrarYConfirmar(existente, jwtComprador);
    }

    /**
     * Paso 1 (validaciones, sin reservar nada) y Tx1 (reserva). NIT y nombre salen de cliente_ref, nunca del cuerpo.
     */
    private Compra reservar(UsuarioAutenticado comprador, CompraRequest request) {
        ClienteRef cliente = validador.validarComprador(comprador.id());
        ViajeRef viaje = validador.validarViaje(request.idViaje());
        List<Short> asientos = validador.validarAsientos(request.asientos(), viaje);
        Compra nueva = Compra.builder()
                .id(request.idCompra())
                .idViaje(viaje.getId())
                .canal(Canal.EN_LINEA)
                .cantidadBoletos((short) asientos.size())
                .precioUnitario(viaje.getPrecioBoleto())
                .montoTotal(dinero.escalar(viaje.getPrecioBoleto().multiply(BigDecimal.valueOf(asientos.size()))))
                .idUsuarioComprador(comprador.id())
                .nitComprador(cliente.getNit())
                .nombreComprador(cliente.getNombreCompleto())
                .fechaPago(request.fechaPago())
                .idSucursalIngreso(viaje.getIdSucursalOrigen())
                .build();
        return gestor.reservar(nueva, asientos);
    }

    /**
     * Pasos 3 y 4: cobro fuera de toda transacción y luego Tx2 (o la compensación).
     */
    private ResultadoCompra cobrarYConfirmar(Compra compra, String jwtComprador) {
        UUID idPago;
        try {
            idPago = pasarelaPago.cobrar(jwtComprador, compra.getId(), compra.getMontoTotal(), compra.getFechaPago(),
                    "Compra de " + compra.getCantidadBoletos() + " boleto(s)");
        } catch (PagoRechazadoException e) {
            // Compensación: se liberan los asientos y se propaga el motivo de Cartera
            gestor.liberar(compra.getId(), e.getCodigo());
            throw e;
        } catch (CarteraNoDisponibleException e) {
            // No se sabe si el cobro salió: la compra sigue RESERVADA y el job de recuperación lo averigua
            log.warn("Compra {} pendiente de verificar: {}", compra.getId(), e.getMessage());
            return new ResultadoCompra(compraMapper.toResponse(compra), ResultadoCompra.Tipo.PENDIENTE);
        }
        GestorCompras.Confirmacion confirmacion = gestor.confirmar(compra.getId(), idPago);
        if (confirmacion.anulada()) {
            throw new ViajeNoDisponibleException(
                    "El viaje fue cancelado mientras se procesaba el pago; el monto se reembolsará a su cartera");
        }
        return new ResultadoCompra(compraMapper.toResponse(confirmacion.compra()), ResultadoCompra.Tipo.CONFIRMADA);
    }
}
