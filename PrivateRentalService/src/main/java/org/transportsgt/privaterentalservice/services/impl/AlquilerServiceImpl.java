package org.transportsgt.privaterentalservice.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.privaterentalservice.clients.CarteraClient;
import org.transportsgt.privaterentalservice.config.security.UsuarioAutenticado;
import org.transportsgt.privaterentalservice.dto.request.CotizacionRequest;
import org.transportsgt.privaterentalservice.dto.request.PagoAlquilerRequest;
import org.transportsgt.privaterentalservice.dto.request.SolicitudAlquilerRequest;
import org.transportsgt.privaterentalservice.dto.request.SolicitudMostradorRequest;
import org.transportsgt.privaterentalservice.dto.response.AlquilerResponse;
import org.transportsgt.privaterentalservice.eventos.EventosAlquiler;
import org.transportsgt.privaterentalservice.exception.AlquilerNoEncontradoException;
import org.transportsgt.privaterentalservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.privaterentalservice.exception.ReglaNegocioException;
import org.transportsgt.privaterentalservice.exception.SolicitudInvalidaException;
import org.transportsgt.privaterentalservice.exception.TransicionInvalidaException;
import org.transportsgt.privaterentalservice.mappers.AlquilerMapper;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.models.ClienteRef;
import org.transportsgt.privaterentalservice.models.CotizacionAlquiler;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;
import org.transportsgt.privaterentalservice.models.SucursalRef;
import org.transportsgt.privaterentalservice.models.TarifaAlquiler;
import org.transportsgt.privaterentalservice.models.TipoCotizacion;
import org.transportsgt.privaterentalservice.repositories.AlquilerRepository;
import org.transportsgt.privaterentalservice.repositories.ClienteRefRepository;
import org.transportsgt.privaterentalservice.repositories.CotizacionAlquilerRepository;
import org.transportsgt.privaterentalservice.repositories.SucursalRefRepository;
import org.transportsgt.privaterentalservice.repositories.TarifaAlquilerRepository;
import org.transportsgt.privaterentalservice.services.AlquilerService;
import org.transportsgt.privaterentalservice.services.CalculadoraDistancia;
import org.transportsgt.eventos.payload.Canal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Period;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlquilerServiceImpl implements AlquilerService {

    private final AlquilerRepository alquilerRepository;
    private final CotizacionAlquilerRepository cotizacionRepository;
    private final TarifaAlquilerRepository tarifaRepository;
    private final SucursalRefRepository sucursalRepository;
    private final ClienteRefRepository clienteRepository;
    private final CalculadoraDistancia calculadora;
    private final CarteraClient carteraClient;
    private final EventosAlquiler eventos;
    private final AlquilerMapper mapper;

    @Override
    @Transactional
    public AlquilerResponse solicitarEnLinea(SolicitudAlquilerRequest req, UsuarioAutenticado usuario) {
        ClienteRef cliente = clienteRepository.findById(usuario.id())
                .orElseThrow(() -> new ReglaNegocioException("PERFIL_INCOMPLETO",
                        "Registre su perfil comercial (NIT y nombre) antes de solicitar un alquiler"));
        if (cliente.getNit() == null || cliente.getNit().isBlank()) {
            throw new ReglaNegocioException("PERFIL_INCOMPLETO",
                    "Su perfil no tiene NIT: complete el perfil antes de solicitar");
        }
        validarSucursal(req.idSucursalOrigen());
        Alquiler a = construir(req, Canal.EN_LINEA, usuario.id(),
                cliente.getNit(), cliente.getNombreCompleto());
        return guardarYPublicarSolicitado(a);
    }

    @Override
    @Transactional
    public AlquilerResponse solicitarMostrador(SolicitudMostradorRequest req, UsuarioAutenticado usuario) {
        if (!usuario.esAdminSucursal() && usuario.rol() != org.transportsgt.eventos.payload.Rol.CAJERO) {
            throw new OperacionNoAutorizadaException("Solo administradores de sucursal y cajeros registran en mostrador");
        }
        SolicitudAlquilerRequest s = req.solicitud();
        UUID sucursal = usuario.perteneceASucursal(s.idSucursalOrigen())
                || usuario.esAdminSistema() ? s.idSucursalOrigen() : usuario.idSucursal();
        validarSucursal(sucursal);
        String nit = req.nitCliente() == null || req.nitCliente().isBlank() ? "CF" : req.nitCliente().trim().toUpperCase();
        String nombre = req.nombreCliente() == null || req.nombreCliente().isBlank()
                ? "Cliente mostrador" : req.nombreCliente().trim();
        Alquiler a = construir(s, Canal.MOSTRADOR, null, nit, nombre);
        a.setIdSucursalOrigen(sucursal);
        return guardarYPublicarSolicitado(a);
    }

    private Alquiler construir(SolicitudAlquilerRequest req, Canal canal, UUID idUsuario, String nit, String nombre) {
        if (req.esIdaYVuelta() && req.fechaHoraRetorno() == null) {
            throw new SolicitudInvalidaException("Un alquiler ida y vuelta requiere fechaHoraRetorno");
        }
        if (!req.esIdaYVuelta() && req.fechaHoraRetorno() != null) {
            throw new SolicitudInvalidaException("fechaHoraRetorno solo aplica a ida y vuelta");
        }
        if (req.fechaHoraRetorno() != null && !req.fechaHoraRetorno().isAfter(req.fechaHoraSalida())) {
            throw new SolicitudInvalidaException("La fecha de retorno debe ser posterior a la salida");
        }
        if (req.fechaHoraSalida().isBefore(OffsetDateTime.now())) {
            throw new SolicitudInvalidaException("La fecha de salida ya pasó");
        }

        BigDecimal distancia;
        if (req.tieneCoordenadas()) {
            distancia = calculadora.distanciaCarretera(
                    req.latitudOrigen(), req.longitudOrigen(), req.latitudDestino(), req.longitudDestino());
        } else if (req.distanciaEstimadaKm() != null) {
            distancia = req.distanciaEstimadaKm();
        } else {
            throw new SolicitudInvalidaException(
                    "Indique las 4 coordenadas o la distanciaEstimadaKm para calcular el precio");
        }

        BigDecimal precio = estimarPrecio(distancia, req.esIdaYVuelta(), req.fechaHoraSalida(), req.fechaHoraRetorno());

        return Alquiler.builder()
                .canal(canal)
                .estado(EstadoAlquiler.SOLICITADO)
                .idUsuarioCliente(idUsuario)
                .nombreCliente(nombre)
                .nitCliente(nit)
                .idSucursalOrigen(req.idSucursalOrigen())
                .origen(req.origen())
                .destino(req.destino())
                .fechaHoraSalida(req.fechaHoraSalida())
                .fechaHoraRetorno(req.fechaHoraRetorno())
                .idaYVuelta(req.esIdaYVuelta())
                .numeroPasajeros(req.numeroPasajeros().shortValue())
                .latitudOrigen(req.latitudOrigen())
                .longitudOrigen(req.longitudOrigen())
                .latitudDestino(req.latitudDestino())
                .longitudDestino(req.longitudDestino())
                .distanciaEstimadaKm(distancia)
                .precioEstimado(precio)
                .build();
    }

    /** distancia × precio_por_km × (2 si ida y vuelta) + días × precio_por_dia. */
    private BigDecimal estimarPrecio(BigDecimal distancia, boolean idaYVuelta,
                                     OffsetDateTime salida, OffsetDateTime retorno) {
        TarifaAlquiler tarifa = tarifaRepository
                .findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(LocalDate.now())
                .orElseThrow(() -> new ReglaNegocioException("SIN_TARIFA",
                        "No hay tarifa de alquiler vigente: contacte al administrador de sistema"));
        BigDecimal distanciaTotal = idaYVuelta ? distancia.multiply(BigDecimal.valueOf(2)) : distancia;
        BigDecimal porKm = distanciaTotal.multiply(tarifa.getPrecioPorKm());
        int dias = Math.max(1, calcularDias(salida, retorno, idaYVuelta));
        BigDecimal porDia = tarifa.getPrecioPorDia().multiply(BigDecimal.valueOf(dias));
        return porKm.add(porDia).setScale(2, RoundingMode.HALF_UP);
    }

    private int calcularDias(OffsetDateTime salida, OffsetDateTime retorno, boolean idaYVuelta) {
        if (idaYVuelta && retorno != null) {
            return Math.max(1, Period.between(salida.toLocalDate(), retorno.toLocalDate()).getDays() + 1);
        }
        long horas = Duration.between(salida, salida.plusDays(1)).toHours();
        return Math.max(1, (int) Math.ceil(horas / 24.0));
    }

    private AlquilerResponse guardarYPublicarSolicitado(Alquiler a) {
        a = alquilerRepository.save(a);
        cotizacionRepository.save(CotizacionAlquiler.builder()
                .alquiler(a).tipo(TipoCotizacion.ESTIMADA).precio(a.getPrecioEstimado())
                .comentario("Estimado automático por tarifa vigente")
                .build());
        eventos.alquilerSolicitado(a);
        log.info("Alquiler {} solicitado ({} → {}, precio estimado {})",
                a.getId(), a.getOrigen(), a.getDestino(), a.getPrecioEstimado());
        return mapper.toResponse(a);
    }

    @Override
    @Transactional
    public AlquilerResponse cotizar(UUID id, CotizacionRequest req, UsuarioAutenticado usuario) {
        Alquiler a = cargarParaAdmin(id, usuario);
        if (a.getEstado() != EstadoAlquiler.SOLICITADO && a.getEstado() != EstadoAlquiler.COTIZADO) {
            throw new TransicionInvalidaException(a.getEstado(), "cotizar");
        }
        a.setPrecioFinal(req.precioFinal());
        a.setFechaHoraLlegadaEstimada(req.fechaHoraLlegadaEstimada());
        a.setEstado(EstadoAlquiler.COTIZADO);
        alquilerRepository.save(a);
        cotizacionRepository.save(CotizacionAlquiler.builder()
                .alquiler(a).tipo(TipoCotizacion.AJUSTADA_ADMIN).precio(req.precioFinal())
                .idUsuario(usuario.id()).comentario(req.comentario())
                .build());
        eventos.alquilerCotizado(a, usuario.id());
        log.info("Alquiler {} cotizado a {} por {}", a.getId(), req.precioFinal(), usuario.id());
        return mapper.toResponse(a);
    }

    @Override
    @Transactional
    public AlquilerResponse pagarEnLinea(UUID id, PagoAlquilerRequest req, UsuarioAutenticado usuario,
                                         String authorizationHeader) {
        Alquiler a = alquilerRepository.findById(id)
                .orElseThrow(() -> new AlquilerNoEncontradoException(id));
        if (a.getIdUsuarioCliente() == null || !a.getIdUsuarioCliente().equals(usuario.id())) {
            throw new OperacionNoAutorizadaException("Solo el cliente dueño del alquiler puede pagarlo en línea");
        }
        if (a.getCanal() != Canal.EN_LINEA) {
            throw new SolicitudInvalidaException("Un alquiler de mostrador se paga con pago-mostrador");
        }
        if (a.getEstado() != EstadoAlquiler.COTIZADO) {
            throw new TransicionInvalidaException(a.getEstado(), "pagar (requiere COTIZADO)");
        }
        UUID idPago = carteraClient.pagar(authorizationHeader, a.getId(), a.getPrecioFinal(),
                req.fechaPago(), "Alquiler " + a.getOrigen() + " → " + a.getDestino());
        a.setIdPago(idPago);
        a.setFechaPago(req.fechaPago());
        a.setEstado(EstadoAlquiler.CONFIRMADO);
        alquilerRepository.save(a);
        eventos.alquilerConfirmado(a);
        log.info("Alquiler {} confirmado con pago {}", a.getId(), idPago);
        return mapper.toResponse(a);
    }

    @Override
    @Transactional
    public AlquilerResponse pagarMostrador(UUID id, PagoAlquilerRequest req, UsuarioAutenticado usuario) {
        if (!usuario.esAdminSucursal() && usuario.rol() != org.transportsgt.eventos.payload.Rol.CAJERO) {
            throw new OperacionNoAutorizadaException("Solo administradores de sucursal y cajeros cobran en mostrador");
        }
        Alquiler a = cargarParaAdmin(id, usuario);
        if (a.getCanal() != Canal.MOSTRADOR) {
            useSoloMostrador();
        }
        if (a.getEstado() != EstadoAlquiler.COTIZADO) {
            throw new TransicionInvalidaException(a.getEstado(), "pagar (requiere COTIZADO)");
        }
        a.setFechaPago(req.fechaPago());
        a.setEstado(EstadoAlquiler.CONFIRMADO);
        alquilerRepository.save(a);
        eventos.alquilerConfirmado(a);
        log.info("Alquiler {} confirmado por pago en efectivo en mostrador", a.getId());
        return mapper.toResponse(a);
    }

    private void useSoloMostrador() {
        throw new SolicitudInvalidaException("Un alquiler en línea se paga con /pago (cartera)");
    }

    @Override
    @Transactional(readOnly = true)
    public AlquilerResponse detalle(UUID id, UsuarioAutenticado usuario) {
        Alquiler a = alquilerRepository.findById(id)
                .orElseThrow(() -> new AlquilerNoEncontradoException(id));
        if (!puedeVer(a, usuario)) {
            throw new OperacionNoAutorizadaException("Este alquiler no le pertenece");
        }
        AlquilerResponse resp = mapper.toResponse(a);
        return new AlquilerResponse(
                resp.id(), resp.canal(), resp.estado(), resp.idUsuarioCliente(), resp.nombreCliente(),
                resp.nitCliente(), resp.idSucursalOrigen(), resp.origen(), resp.destino(),
                resp.fechaHoraSalida(), resp.fechaHoraRetorno(), resp.fechaHoraLlegadaEstimada(),
                resp.idaYVuelta(), resp.numeroPasajeros(),
                resp.latitudOrigen(), resp.longitudOrigen(), resp.latitudDestino(), resp.longitudDestino(),
                resp.distanciaEstimadaKm(), resp.precioEstimado(), resp.precioFinal(),
                resp.idPago(), resp.fechaPago(), resp.idViaje(), resp.idBus(), resp.placaBus(),
                resp.idChofer(), resp.nombreChofer(), resp.motivoCancelacion(), resp.canceladoEn(),
                resp.version(), resp.creadoEn(),
                mapper.toCotizacionList(cotizacionRepository.findAllByAlquilerIdOrderByCreadoEn(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AlquilerResponse> listar(EstadoAlquiler estado, LocalDate desde, LocalDate hasta,
                                         Boolean sinAsignar, UsuarioAutenticado usuario, Pageable pageable) {
        Specification<Alquiler> spec = (root, query, cb) -> cb.conjunction();
        if (estado != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("estado"), estado));
        }
        if (desde != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(
                    root.get("fechaHoraSalida"), desde.atStartOfDay().atOffset(java.time.ZoneOffset.UTC)));
        }
        if (hasta != null) {
            spec = spec.and((root, q, cb) -> cb.lessThan(
                    root.get("fechaHoraSalida"), hasta.plusDays(1).atStartOfDay().atOffset(java.time.ZoneOffset.UTC)));
        }
        if (Boolean.TRUE.equals(sinAsignar)) {
            spec = spec.and((root, q, cb) -> cb.isNull(root.get("idViaje")));
            spec = spec.and((root, q, cb) -> cb.equal(root.get("estado"), EstadoAlquiler.CONFIRMADO));
        }
        if (!usuario.esAdminSistema()) {
            UUID sucursal = usuario.idSucursal();
            spec = spec.and((root, q, cb) -> cb.equal(root.get("idSucursalOrigen"), sucursal));
        }
        return alquilerRepository.findAll(spec, pageable).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AlquilerResponse> mios(UsuarioAutenticado usuario, Pageable pageable) {
        String nit = clienteRepository.findById(usuario.id())
                .map(ClienteRef::getNit).orElse(null);
        return alquilerRepository.findHistorial(usuario.id(), nit, pageable).map(mapper::toResponse);
    }

    private boolean puedeVer(Alquiler a, UsuarioAutenticado usuario) {
        if (usuario.esAdminSistema()) {
            return true;
        }
        if (a.getIdUsuarioCliente() != null && a.getIdUsuarioCliente().equals(usuario.id())) {
            return true;
        }
        return usuario.perteneceASucursal(a.getIdSucursalOrigen());
    }

    private Alquiler cargarParaAdmin(UUID id, UsuarioAutenticado usuario) {
        Alquiler a = alquilerRepository.findById(id)
                .orElseThrow(() -> new AlquilerNoEncontradoException(id));
        if (!usuario.esAdminSistema() && !usuario.perteneceASucursal(a.getIdSucursalOrigen())) {
            throw new OperacionNoAutorizadaException("El alquiler pertenece a otra sucursal");
        }
        return a;
    }

    private void validarSucursal(UUID idSucursal) {
        SucursalRef s = sucursalRepository.findById(idSucursal)
                .orElseThrow(() -> new SolicitudInvalidaException("No existe la sucursal " + idSucursal));
        if (!Boolean.TRUE.equals(s.getActiva())) {
            throw new SolicitudInvalidaException("La sucursal de origen está inactiva");
        }
    }
}
