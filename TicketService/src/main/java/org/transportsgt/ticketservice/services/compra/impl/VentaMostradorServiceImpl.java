package org.transportsgt.ticketservice.services.compra.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.ticketservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.ticketservice.dto.request.VentaMostradorRequest;
import org.transportsgt.ticketservice.dto.response.CompraResponse;
import org.transportsgt.ticketservice.dto.response.Resultado;
import org.transportsgt.ticketservice.exception.NitInvalidoException;
import org.transportsgt.ticketservice.exception.SinPermisoSucursalException;
import org.transportsgt.ticketservice.mappers.CompraMapper;
import org.transportsgt.ticketservice.models.ClienteRef;
import org.transportsgt.ticketservice.models.Compra;
import org.transportsgt.ticketservice.models.ViajeRef;
import org.transportsgt.ticketservice.repositories.ClienteRefRepository;
import org.transportsgt.ticketservice.repositories.CompraRepository;
import org.transportsgt.ticketservice.services.compra.VentaMostradorService;
import org.transportsgt.ticketservice.utils.Dinero;
import org.transportsgt.eventos.soporte.Nit;
import org.transportsgt.eventos.payload.boletos.Canal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VentaMostradorServiceImpl implements VentaMostradorService {

    static final String CONSUMIDOR_FINAL = "Consumidor final";

    private final CompraRepository compraRepository;
    private final ClienteRefRepository clienteRefRepository;
    private final ValidadorCompra validador;
    private final GestorCompras gestor;
    private final CompraMapper compraMapper;
    private final Dinero dinero;

    @Override
    @Transactional
    public Resultado<CompraResponse> vender(UsuarioAutenticado vendedor, VentaMostradorRequest request) {
        Optional<Compra> existente = compraRepository.findConBoletosById(request.idCompra());
        if (existente.isPresent()) {
            validador.verificarRepetida(existente.get(), Canal.MOSTRADOR, vendedor.id(), request.idViaje(),
                    request.asientos());
            return Resultado.existente(compraMapper.toResponse(existente.get()));
        }
        ViajeRef viaje = validador.validarViaje(request.idViaje());
        if (!vendedor.perteneceASucursal(viaje.getIdSucursalOrigen())) {
            throw new SinPermisoSucursalException();
        }
        List<Short> asientos = validador.validarAsientos(request.asientos(), viaje);
        String nit = nitComprador(request.nitComprador());
        Compra nueva = Compra.builder()
                .id(request.idCompra())
                .idViaje(viaje.getId())
                .canal(Canal.MOSTRADOR)
                .cantidadBoletos((short) asientos.size())
                .precioUnitario(viaje.getPrecioBoleto())
                .montoTotal(dinero.escalar(viaje.getPrecioBoleto().multiply(BigDecimal.valueOf(asientos.size()))))
                .nitComprador(nit)
                .nombreComprador(nombreComprador(nit, request.nombreComprador()))
                .fechaPago(request.fechaPago())
                .idSucursalIngreso(viaje.getIdSucursalOrigen())
                .idUsuarioVendedor(vendedor.id())
                .build();
        return Resultado.nuevo(compraMapper.toResponse(gestor.registrarConfirmada(nueva, asientos, viaje.getIdRuta())));
    }

    /**
     * Sin NIT (o {@code CF}) → consumidor final. Con NIT → normalizado y con formato válido.
     */
    private String nitComprador(String nitPedido) {
        String nit = Nit.normalizar(nitPedido);
        if (nit == null || Nit.esConsumidorFinal(nit)) {
            return Nit.CONSUMIDOR_FINAL;
        }
        if (!Nit.tieneFormatoValido(nit)) {
            throw new NitInvalidoException();
        }
        return nit;
    }

    /**
     * El nombre que dio el cajero; si no lo dio, el del perfil con ese NIT; si no hay, "Consumidor final".
     */
    private String nombreComprador(String nit, String nombrePedido) {
        if (nombrePedido != null && !nombrePedido.isBlank()) {
            return nombrePedido.trim();
        }
        if (Nit.esConsumidorFinal(nit)) {
            return CONSUMIDOR_FINAL;
        }
        return clienteRefRepository.findFirstByNit(nit).map(ClienteRef::getNombreCompleto).orElse(CONSUMIDOR_FINAL);
    }
}
