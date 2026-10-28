package org.transportsgt.ticketservice.services.consulta.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.ticketservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.ticketservice.documentos.BoletosPdf;
import org.transportsgt.ticketservice.exception.CompraNoConfirmadaException;
import org.transportsgt.ticketservice.exception.CompraNoEncontradaException;
import org.transportsgt.ticketservice.exception.ViajeNoEncontradoException;
import org.transportsgt.ticketservice.models.Boleto;
import org.transportsgt.ticketservice.models.Compra;
import org.transportsgt.ticketservice.models.EstadoBoleto;
import org.transportsgt.ticketservice.models.EstadoCompra;
import org.transportsgt.ticketservice.models.ViajeRef;
import org.transportsgt.ticketservice.repositories.CompraRepository;
import org.transportsgt.ticketservice.repositories.ViajeRefRepository;
import org.transportsgt.ticketservice.services.consulta.BoletosPdfService;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoletosPdfServiceImpl implements BoletosPdfService {

    private final CompraRepository compraRepository;
    private final ViajeRefRepository viajeRefRepository;
    private final ReglasVisibilidad visibilidad;
    private final BoletosPdf boletosPdf;

    @Override
    public byte[] generar(UsuarioAutenticado usuario, UUID idCompra) {
        Compra compra = compraRepository.findConBoletosById(idCompra)
                .filter(encontrada -> visibilidad.puedeVer(usuario, encontrada))
                .orElseThrow(CompraNoEncontradaException::new);
        if (compra.getEstado() != EstadoCompra.CONFIRMADA) {
            throw new CompraNoConfirmadaException(compra.getEstado().name());
        }
        ViajeRef viaje = viajeRefRepository.findById(compra.getIdViaje()).orElseThrow(ViajeNoEncontradoException::new);
        List<Boleto> boletos = compra.getBoletos().stream()
                .filter(boleto -> boleto.getEstado() == EstadoBoleto.CONFIRMADO)
                .sorted(Comparator.comparing(Boleto::getNumeroAsiento))
                .toList();
        return boletosPdf.generar(compra, viaje, boletos);
    }
}
