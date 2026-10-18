package org.transportsgt.operationalcostsservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.LineaTaller;
import org.transportsgt.operationalcostsservice.config.security.UsuarioAutenticado;
import org.transportsgt.operationalcostsservice.dto.request.CategoriaActivaRequest;
import org.transportsgt.operationalcostsservice.dto.request.CategoriaRequest;
import org.transportsgt.operationalcostsservice.dto.request.GastoTallerRequest;
import org.transportsgt.operationalcostsservice.dto.request.TarifaDepreciacionRequest;
import org.transportsgt.operationalcostsservice.dto.response.CategoriaResponse;
import org.transportsgt.operationalcostsservice.dto.response.GastoTallerResponse;
import org.transportsgt.operationalcostsservice.dto.response.ResumenCostosViajeResponse;
import org.transportsgt.operationalcostsservice.dto.response.TarifaDepreciacionResponse;
import org.transportsgt.operationalcostsservice.eventos.EventosCostos;
import org.transportsgt.operationalcostsservice.exception.NoEncontradoException;
import org.transportsgt.operationalcostsservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.operationalcostsservice.exception.ReglaNegocioException;
import org.transportsgt.operationalcostsservice.exception.ValidacionException;
import org.transportsgt.operationalcostsservice.mappers.CostosMapper;
import org.transportsgt.operationalcostsservice.models.BusRef;
import org.transportsgt.operationalcostsservice.models.CategoriaGasto;
import org.transportsgt.operationalcostsservice.models.ConfiguracionDepreciacion;
import org.transportsgt.operationalcostsservice.models.GastoCombustible;
import org.transportsgt.operationalcostsservice.models.GastoTaller;
import org.transportsgt.operationalcostsservice.models.GastoTallerLinea;
import org.transportsgt.operationalcostsservice.models.PagoChofer;
import org.transportsgt.operationalcostsservice.models.RegistroDepreciacion;
import org.transportsgt.operationalcostsservice.repositories.BusRefRepository;
import org.transportsgt.operationalcostsservice.repositories.CategoriaGastoRepository;
import org.transportsgt.operationalcostsservice.repositories.ConfiguracionDepreciacionRepository;
import org.transportsgt.operationalcostsservice.repositories.GastoCombustibleRepository;
import org.transportsgt.operationalcostsservice.repositories.GastoTallerLineaRepository;
import org.transportsgt.operationalcostsservice.repositories.GastoTallerRepository;
import org.transportsgt.operationalcostsservice.repositories.PagoChoferRepository;
import org.transportsgt.operationalcostsservice.repositories.RegistroDepreciacionRepository;
import org.transportsgt.operationalcostsservice.services.CostosService;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CostosServiceImpl implements CostosService {

    private final ConfiguracionDepreciacionRepository tarifaRepository;
    private final CategoriaGastoRepository categoriaRepository;
    private final GastoTallerRepository gastoTallerRepository;
    private final GastoTallerLineaRepository gastoTallerLineaRepository;
    private final BusRefRepository busRefRepository;
    private final GastoCombustibleRepository combustibleRepository;
    private final RegistroDepreciacionRepository depreciacionRepository;
    private final PagoChoferRepository pagoChoferRepository;
    private final CostosMapper mapper;
    private final EventosCostos eventos;
    private final Clock clock;

    // ---------- Depreciación ----------

    @Override
    @Transactional(readOnly = true)
    public TarifaDepreciacionResponse tarifaVigente(LocalDate fecha) {
        LocalDate f = fecha != null ? fecha : LocalDate.now(clock);
        return tarifaRepository.findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(f)
                .map(mapper::toResponse)
                .orElseThrow(() -> new NoEncontradoException("SIN_TARIFA",
                        "No hay tarifa de depreciación vigente para la fecha " + f));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TarifaDepreciacionResponse> historialTarifas() {
        return mapper.toTarifaList(tarifaRepository.findAllByOrderByVigenteDesdeDesc());
    }

    @Override
    @Transactional
    public TarifaDepreciacionResponse cambiarTarifa(TarifaDepreciacionRequest request, UsuarioAutenticado usuario) {
        if (!usuario.esAdminSistema()) {
            throw new OperacionNoAutorizadaException("Solo ADMIN_SISTEMA puede cambiar la tarifa de depreciación");
        }
        tarifaRepository.guardarTarifa(request.montoPorKm(), request.vigenteDesde(), usuario.id());
        tarifaRepository.flush();
        ConfiguracionDepreciacion tarifa = tarifaRepository
                .findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(request.vigenteDesde())
                .orElseThrow(() -> new NoEncontradoException("SIN_TARIFA", "Tarifa no guardada"));
        eventos.tarifaDepreciacionCambiada(mapper.toDatosTarifa(
                request.montoPorKm(), request.vigenteDesde(), usuario.id()));
        return mapper.toResponse(tarifa);
    }

    // ---------- Categorías ----------

    @Override
    @Transactional(readOnly = true)
    public List<CategoriaResponse> listarCategorias(Boolean activa) {
        List<CategoriaGasto> lista = Boolean.TRUE.equals(activa)
                ? categoriaRepository.findAllByActivaTrueOrderByNombre()
                : categoriaRepository.findAllByOrderByNombre();
        return mapper.toCategoriaList(lista);
    }

    @Override
    @Transactional
    public CategoriaResponse crearCategoria(CategoriaRequest request) {
        if (categoriaRepository.existsByNombreIgnoreCase(request.nombre())) {
            throw new ReglaNegocioException("CATEGORIA_DUPLICADA", "Ya existe una categoría con ese nombre");
        }
        CategoriaGasto c = CategoriaGasto.builder()
                .nombre(request.nombre())
                .descripcion(request.descripcion())
                .activa(true)
                .build();
        return mapper.toResponse(categoriaRepository.save(c));
    }

    @Override
    @Transactional
    public CategoriaResponse actualizarCategoria(UUID id, CategoriaRequest request) {
        CategoriaGasto c = categoriaRepository.findById(id)
                .orElseThrow(() -> new NoEncontradoException("CATEGORIA_NO_ENCONTRADA",
                        "No existe la categoría " + id));
        if (categoriaRepository.existsByNombreIgnoreCaseAndIdNot(request.nombre(), id)) {
            throw new ReglaNegocioException("CATEGORIA_DUPLICADA", "Ya existe otra categoría con ese nombre");
        }
        c.setNombre(request.nombre());
        c.setDescripcion(request.descripcion());
        return mapper.toResponse(categoriaRepository.save(c));
    }

    @Override
    @Transactional
    public CategoriaResponse cambiarActivaCategoria(UUID id, CategoriaActivaRequest request) {
        CategoriaGasto c = categoriaRepository.findById(id)
                .orElseThrow(() -> new NoEncontradoException("CATEGORIA_NO_ENCONTRADA",
                        "No existe la categoría " + id));
        c.setActiva(request.activa());
        return mapper.toResponse(categoriaRepository.save(c));
    }

    @Override
    @Transactional
    public void eliminarCategoria(UUID id) {
        CategoriaGasto c = categoriaRepository.findById(id)
                .orElseThrow(() -> new NoEncontradoException("CATEGORIA_NO_ENCONTRADA",
                        "No existe la categoría " + id));
        if (gastoTallerLineaRepository.existsByCategoriaId(id)) {
            throw new ReglaNegocioException("CATEGORIA_CON_GASTOS",
                    "La categoría tiene gastos asociados; desactívela en lugar de eliminarla");
        }
        categoriaRepository.delete(c);
    }

    // ---------- Taller ----------

    @Override
    @Transactional
    public GastoTallerResponse registrarGastoTaller(GastoTallerRequest request, UsuarioAutenticado usuario) {
        BusRef bus = busRefRepository.findById(request.idBus())
                .orElseThrow(() -> new NoEncontradoException("BUS_NO_ENCONTRADO",
                        "No existe el bus " + request.idBus() + " en la proyección de flota"));
        if (!bus.getActivo()) {
            throw new ValidacionException("BUS_INACTIVO", "El bus no está activo");
        }
        if (usuario.esAdminSucursal() && !bus.getIdSucursal().equals(usuario.idSucursal())) {
            throw new OperacionNoAutorizadaException(
                    "El bus pertenece a otra sucursal; solo puede registrar gastos de su propia sucursal");
        }

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

        List<LineaTaller> lineasEvent = gasto.getLineas().stream()
                .map(l -> new LineaTaller(l.getId(), l.getCategoria().getId(), l.getCategoria().getNombre(),
                        l.getDescripcion(), l.getTipo(), l.getMonto()))
                .toList();
        eventos.gastoTallerRegistrado(mapper.toDatosGastoTaller(gasto, lineasEvent));

        return mapper.toResponse(gastoTallerRepository.findConLineasById(gasto.getId()).orElse(gasto));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GastoTallerResponse> listarGastosTaller(UUID idBus, LocalDate desde, LocalDate hasta,
                                                        UsuarioAutenticado usuario, Pageable pageable) {
        Specification<GastoTaller> spec = (root, query, cb) -> {
            List<Predicate> pred = new ArrayList<>();
            if (usuario.esAdminSucursal()) {
                pred.add(cb.equal(root.get("idSucursal"), usuario.idSucursal()));
            }
            if (idBus != null) {
                pred.add(cb.equal(root.get("idBus"), idBus));
            }
            if (desde != null) {
                pred.add(cb.greaterThanOrEqualTo(root.get("fechaServicio"), desde));
            }
            if (hasta != null) {
                pred.add(cb.lessThanOrEqualTo(root.get("fechaServicio"), hasta));
            }
            return cb.and(pred.toArray(new Predicate[0]));
        };
        return gastoTallerRepository.findAll(spec, pageable).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public GastoTallerResponse detalleGastoTaller(UUID id, UsuarioAutenticado usuario) {
        GastoTaller g = gastoTallerRepository.findConLineasById(id)
                .orElseThrow(() -> new NoEncontradoException("GASTO_NO_ENCONTRADO",
                        "No existe el gasto de taller " + id));
        if (usuario.esAdminSucursal() && !g.getIdSucursal().equals(usuario.idSucursal())) {
            throw new OperacionNoAutorizadaException("El gasto de taller pertenece a otra sucursal");
        }
        return mapper.toResponse(g);
    }

    // ---------- Viaje ----------

    @Override
    @Transactional(readOnly = true)
    public ResumenCostosViajeResponse resumenViaje(UUID idViaje, UsuarioAutenticado usuario) {
        if (usuario.esAdminSucursal()) {
            boolean pertenece = combustibleRepository.findByIdViaje(idViaje)
                    .map(c -> c.getIdSucursal().equals(usuario.idSucursal()))
                    .or(() -> depreciacionRepository.findByIdViaje(idViaje)
                            .map(d -> d.getIdSucursal().equals(usuario.idSucursal())))
                    .orElse(false);
            if (!pertenece) {
                throw new OperacionNoAutorizadaException("El viaje no tiene costos en su sucursal");
            }
        }
        GastoCombustible comb = combustibleRepository.findByIdViaje(idViaje).orElse(null);
        RegistroDepreciacion dep = depreciacionRepository.findByIdViaje(idViaje).orElse(null);
        PagoChofer pago = pagoChoferRepository.findByIdViaje(idViaje).orElse(null);
        if (comb == null && dep == null && pago == null) {
            throw new NoEncontradoException("VIAJE_SIN_COSTOS",
                    "El viaje " + idViaje + " no tiene costos registrados");
        }
        return new ResumenCostosViajeResponse(
                idViaje,
                comb != null ? mapper.toCombustibleResponse(comb) : null,
                dep != null ? mapper.toDepreciacionResponse(dep) : null,
                pago != null ? mapper.toPagoChoferResponse(pago) : null);
    }
}
