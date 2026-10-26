package org.transportsgt.ticketservice.services.consulta.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.ticketservice.config.security.UsuarioAutenticado;
import org.transportsgt.ticketservice.dto.response.CompraResponse;
import org.transportsgt.ticketservice.dto.response.PaginaResponse;
import org.transportsgt.ticketservice.exception.CompraNoEncontradaException;
import org.transportsgt.ticketservice.mappers.CompraMapper;
import org.transportsgt.ticketservice.models.Compra;
import org.transportsgt.ticketservice.models.EstadoCompra;
import org.transportsgt.ticketservice.repositories.CompraRepository;
import org.transportsgt.ticketservice.repositories.CompraSpecs;
import org.transportsgt.ticketservice.services.consulta.ConsultaComprasService;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultaComprasServiceImpl implements ConsultaComprasService {

    private static final Sort RECIENTES_PRIMERO = Sort.by(Sort.Direction.DESC, "creadoEn");

    private final CompraRepository compraRepository;
    private final CompraSpecs specs;
    private final ReglasVisibilidad visibilidad;
    private final CompraMapper compraMapper;

    @Override
    public PaginaResponse<CompraResponse> mias(UsuarioAutenticado usuario, EstadoCompra estado, int pagina, int tamano) {
        PageRequest pedido = PageRequest.of(pagina, tamano);
        Optional<String> nit = visibilidad.nitDe(usuario);
        Page<Compra> compras = nit.isPresent()
                ? compraRepository.findHistorial(usuario.id(), nit.get(), estado, pedido)
                : compraRepository.findHistorialSinNit(usuario.id(), estado, pedido);
        return PaginaResponse.de(compras, compraMapper::toResponse);
    }

    @Override
    public CompraResponse detalle(UsuarioAutenticado usuario, UUID idCompra) {
        return compraRepository.findConBoletosById(idCompra)
                .filter(compra -> visibilidad.puedeVer(usuario, compra))
                .map(compraMapper::toResponse)
                .orElseThrow(CompraNoEncontradaException::new);
    }

    @Override
    public PaginaResponse<CompraResponse> listado(UsuarioAutenticado usuario, UUID idViaje, EstadoCompra estado,
                                                  int pagina, int tamano) {
        return PaginaResponse.de(compraRepository.findAll(specs.filtrar(usuario.idSucursal(), idViaje, estado),
                PageRequest.of(pagina, tamano, RECIENTES_PRIMERO)), compraMapper::toResponse);
    }
}
