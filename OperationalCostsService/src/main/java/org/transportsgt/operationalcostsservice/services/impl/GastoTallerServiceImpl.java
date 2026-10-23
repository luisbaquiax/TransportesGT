package org.transportsgt.operationalcostsservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.operationalcostsservice.config.security.PoliticaAccesoSucursal;
import org.transportsgt.operationalcostsservice.config.security.UsuarioAutenticado;
import org.transportsgt.operationalcostsservice.dto.request.GastoTallerRequest;
import org.transportsgt.operationalcostsservice.dto.response.GastoTallerResponse;
import org.transportsgt.operationalcostsservice.eventos.EventosCostos;
import org.transportsgt.operationalcostsservice.exception.NoEncontradoException;
import org.transportsgt.operationalcostsservice.exception.ValidacionException;
import org.transportsgt.operationalcostsservice.mappers.CostosMapper;
import org.transportsgt.operationalcostsservice.models.BusRef;
import org.transportsgt.operationalcostsservice.models.CategoriaGasto;
import org.transportsgt.operationalcostsservice.models.GastoTaller;
import org.transportsgt.operationalcostsservice.models.GastoTallerLinea;
import org.transportsgt.operationalcostsservice.repositories.BusRefRepository;
import org.transportsgt.operationalcostsservice.repositories.CategoriaGastoRepository;
import org.transportsgt.operationalcostsservice.repositories.GastoTallerEspecificaciones;
import org.transportsgt.operationalcostsservice.repositories.GastoTallerRepository;
import org.transportsgt.operationalcostsservice.services.GastoTallerService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GastoTallerServiceImpl implements GastoTallerService {

    private final BusRefRepository busRefRepository;
    private final CategoriaGastoRepository categoriaRepository;
    private final GastoTallerRepository gastoTallerRepository;
    private final CostosMapper mapper;
    private final EventosCostos eventos;
    private final PoliticaAccesoSucursal politica;

    @Override
    @Transactional
    public GastoTallerResponse registrarGastoTaller(GastoTallerRequest request, UsuarioAutenticado usuario) {
        BusRef bus = busRefRepository.findById(request.idBus())
                .orElseThrow(() -> new NoEncontradoException("BUS_NO_ENCONTRADO",
                        "No existe el bus " + request.idBus() + " en la proyección de flota"));
        if (!bus.getActivo()) {
            throw new ValidacionException("BUS_INACTIVO", "El bus no está activo");
        }
        politica.exigirMismaSucursal(usuario, bus.getIdSucursal(),
                "El bus pertenece a otra sucursal; solo puede registrar gastos de su propia sucursal");

        List<CategoriaGasto> categorias = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (GastoTallerRequest.LineaTallerRequest linea : request.lineas()) {
            CategoriaGasto cat = categoriaRepository.findById(linea.idCategoria())
                    .orElseThrow(() -> new NoEncontradoException("CATEGORIA_NO_ENCONTRADA",
                            "No existe la categoría " + linea.idCategoria()));
            if (!Boolean.TRUE.equals(cat.getActiva())) {
                throw new ValidacionException("CATEGORIA_INACTIVA",
                        "La categoría '" + cat.getNombre() + "' está inactiva");
            }
            categorias.add(cat);
            total = total.add(linea.monto());
        }

        GastoTaller gasto = GastoTaller.builder()
                .idBus(bus.getId())
                .idSucursal(bus.getIdSucursal())
                .fechaServicio(request.fechaServicio())
                .total(total.setScale(2, RoundingMode.HALF_UP))
                .observaciones(request.observaciones())
                .registradoPor(usuario.id())
                .build();

        for (int i = 0; i < request.lineas().size(); i++) {
            GastoTallerRequest.LineaTallerRequest linea = request.lineas().get(i);
            GastoTallerLinea entidad = GastoTallerLinea.builder()
                    .gastoTaller(gasto)
                    .categoria(categorias.get(i))
                    .descripcion(linea.descripcion())
                    .tipo(linea.tipo())
                    .monto(linea.monto())
                    .build();
            gasto.getLineas().add(entidad);
        }

        gasto = gastoTallerRepository.save(gasto);
        eventos.gastoTallerRegistrado(
                mapper.toDatosGastoTaller(gasto, mapper.toLineaTallerList(gasto.getLineas())));

        return mapper.toResponse(gastoTallerRepository.findConLineasById(gasto.getId()).orElse(gasto));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GastoTallerResponse> listarGastosTaller(UUID idBus, LocalDate desde, LocalDate hasta,
                                                        UsuarioAutenticado usuario, Pageable pageable) {
        UUID idSucursalSolo = usuario.esAdminSucursal() ? usuario.idSucursal() : null;
        return gastoTallerRepository
                .findAll(GastoTallerEspecificaciones.conFiltros(idBus, desde, hasta, idSucursalSolo), pageable)
                .map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public GastoTallerResponse detalleGastoTaller(UUID id, UsuarioAutenticado usuario) {
        GastoTaller g = gastoTallerRepository.findConLineasById(id)
                .orElseThrow(() -> new NoEncontradoException("GASTO_NO_ENCONTRADO",
                        "No existe el gasto de taller " + id));
        politica.exigirMismaSucursal(usuario, g.getIdSucursal(),
                "El gasto de taller pertenece a otra sucursal");
        return mapper.toResponse(g);
    }
}
