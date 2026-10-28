package org.transportsgt.privaterentalservice.services.solicitud.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.boletos.Canal;
import org.transportsgt.privaterentalservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.privaterentalservice.dto.request.SolicitudAlquilerRequest;
import org.transportsgt.privaterentalservice.dto.request.SolicitudMostradorRequest;
import org.transportsgt.privaterentalservice.dto.response.AlquilerResponse;
import org.transportsgt.privaterentalservice.eventos.EventosAlquiler;
import org.transportsgt.privaterentalservice.exception.ReglaNegocioException;
import org.transportsgt.privaterentalservice.mappers.AlquilerMapper;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.models.ClienteRef;
import org.transportsgt.privaterentalservice.models.CotizacionAlquiler;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;
import org.transportsgt.privaterentalservice.models.TipoCotizacion;
import org.transportsgt.privaterentalservice.repositories.ClienteRefRepository;
import org.transportsgt.privaterentalservice.repositories.CotizacionAlquilerRepository;
import org.transportsgt.privaterentalservice.services.autorizacion.AutorizacionAlquiler;
import org.transportsgt.privaterentalservice.services.comun.GuardadoAlquiler;
import org.transportsgt.privaterentalservice.services.comun.NormalizadorNit;
import org.transportsgt.privaterentalservice.services.solicitud.SolicitudAlquilerService;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SolicitudAlquilerServiceImpl implements SolicitudAlquilerService {

    private final ClienteRefRepository clienteRepository;
    private final CotizacionAlquilerRepository cotizacionRepository;
    private final ValidadorSolicitud validador;
    private final EstimadorPrecio estimador;
    private final NormalizadorNit normalizadorNit;
    private final AutorizacionAlquiler autorizacion;
    private final GuardadoAlquiler guardado;
    private final EventosAlquiler eventos;
    private final AlquilerMapper mapper;

    @Override
    @Transactional
    public AlquilerResponse solicitarEnLinea(SolicitudAlquilerRequest req, UsuarioAutenticado usuario) {
        ClienteRef cliente = clienteRepository.findById(usuario.id())
                .orElseThrow(() -> new ReglaNegocioException("PERFIL_INCOMPLETO",
                        "Registre su perfil comercial (NIT y nombre) antes de solicitar un alquiler"));
        String nit = normalizadorNit.normalizar(cliente.getNit());
        if (nit == null) {
            throw new ReglaNegocioException("PERFIL_INCOMPLETO",
                    "Su perfil no tiene NIT: complete el perfil antes de solicitar");
        }
        validador.validarSucursal(req.idSucursalOrigen());
        return registrar(req, Canal.EN_LINEA, req.idSucursalOrigen(), usuario.id(), nit, cliente.getNombreCompleto());
    }

    @Override
    @Transactional
    public AlquilerResponse solicitarMostrador(SolicitudMostradorRequest req, UsuarioAutenticado usuario) {
        autorizacion.verificarPersonalMostrador(usuario,
                "Solo administradores de sucursal y cajeros registran en mostrador");
        SolicitudAlquilerRequest s = req.solicitud();
        // El personal registra en su propia sucursal aunque el cuerpo indique otra
        UUID sucursal = usuario.perteneceASucursal(s.idSucursalOrigen()) || usuario.esAdminSistema()
                ? s.idSucursalOrigen() : usuario.idSucursal();
        validador.validarSucursal(sucursal);
        String nit = normalizadorNit.paraMostrador(req.nitCliente());
        return registrar(s, Canal.MOSTRADOR, sucursal, null, nit, req.nombreCliente().trim());
    }

    private AlquilerResponse registrar(SolicitudAlquilerRequest req, Canal canal, UUID sucursal,
                                       UUID idUsuario, String nit, String nombre) {
        validador.validar(req);
        BigDecimal distancia = estimador.distancia(req);
        BigDecimal precio = estimador.estimar(distancia, req.esIdaYVuelta(),
                req.fechaHoraSalida(), req.fechaHoraRetorno());

        Alquiler a = guardado.guardar(Alquiler.builder()
                .canal(canal)
                .estado(EstadoAlquiler.SOLICITADO)
                .idUsuarioCliente(idUsuario)
                .nombreCliente(nombre)
                .nitCliente(nit)
                .idSucursalOrigen(sucursal)
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
                .build());
        cotizacionRepository.save(CotizacionAlquiler.builder()
                .alquiler(a).tipo(TipoCotizacion.ESTIMADA).precio(precio)
                .comentario("Estimado automático por tarifa vigente")
                .build());
        eventos.alquilerSolicitado(a);
        log.info("Alquiler {} solicitado por {} ({} → {}, precio estimado {})",
                a.getId(), canal, a.getOrigen(), a.getDestino(), precio);
        return mapper.toResponse(a);
    }
}
