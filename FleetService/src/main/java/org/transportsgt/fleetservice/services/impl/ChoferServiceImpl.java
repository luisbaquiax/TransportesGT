package org.transportsgt.fleetservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.transportsgt.fleetservice.config.security.UsuarioAutenticado;
import org.transportsgt.fleetservice.dto.request.ActualizarChoferRequest;
import org.transportsgt.fleetservice.dto.request.CambiarActivoRequest;
import org.transportsgt.fleetservice.dto.request.CrearChoferRequest;
import org.transportsgt.fleetservice.dto.response.ChoferResponse;
import org.transportsgt.fleetservice.dto.response.PaginaResponse;
import org.transportsgt.fleetservice.eventos.EventosFlota;
import org.transportsgt.fleetservice.exception.ChoferNoEncontradoException;
import org.transportsgt.fleetservice.exception.LicenciaDuplicadaException;
import org.transportsgt.fleetservice.exception.FotoNoEncontradaException;
import org.transportsgt.fleetservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.fleetservice.exception.UsuarioYaEnlazadoException;
import org.transportsgt.fleetservice.mappers.ChoferMapper;
import org.transportsgt.fleetservice.models.Chofer;
import org.transportsgt.fleetservice.repositories.ChoferRepository;
import org.transportsgt.fleetservice.repositories.ChoferSpecs;
import org.transportsgt.fleetservice.services.AutorizacionSucursal;
import org.transportsgt.fleetservice.services.ChoferService;
import org.transportsgt.fleetservice.services.FotoService;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChoferServiceImpl implements ChoferService {

    private final ChoferRepository choferRepository;
    private final AutorizacionSucursal autorizacion;
    private final FotoService fotoService;
    private final ChoferMapper choferMapper;
    private final EventosFlota eventos;

    @Override
    @Transactional
    public ChoferResponse crear(CrearChoferRequest request, UsuarioAutenticado autenticado) {
        autorizacion.validarSucursalGestionada(autenticado.idSucursal());
        String licencia = request.numeroLicencia().trim();
        if (choferRepository.existsByNumeroLicencia(licencia)) {
            throw new LicenciaDuplicadaException(licencia);
        }
        if (request.idUsuario() != null && choferRepository.existsByIdUsuario(request.idUsuario())) {
            throw new UsuarioYaEnlazadoException(request.idUsuario());
        }
        Chofer chofer = Chofer.builder()
                .idSucursal(autenticado.idSucursal())
                .nombreCompleto(request.nombreCompleto().trim())
                .correo(request.correo().trim())
                .telefono(request.telefono().trim())
                .numeroLicencia(licencia)
                .tipoLicencia(request.tipoLicencia())
                .fechaVencimientoLicencia(request.fechaVencimientoLicencia())
                .salarioBaseViaje(request.salarioBaseViaje())
                .idUsuario(request.idUsuario())
                .activo(true)
                .build();
        Chofer guardado = choferRepository.saveAndFlush(chofer);
        eventos.choferRegistrado(guardado);
        return choferMapper.toResponse(guardado);
    }

    @Override
    public PaginaResponse<ChoferResponse> listar(UUID idSucursal, Boolean activo, String texto,
                                                 Pageable pageable, UsuarioAutenticado autenticado) {
        UUID filtroSucursal = idSucursal;
        if (autenticado.esAdminSucursal()) {
            if (idSucursal != null && !idSucursal.equals(autenticado.idSucursal())) {
                throw new OperacionNoAutorizadaException("Solo puede consultar los choferes de su sucursal");
            }
            filtroSucursal = autenticado.idSucursal();
        }
        Page<Chofer> pagina = choferRepository.findAll(
                ChoferSpecs.filtrar(filtroSucursal, activo, texto), pageable);
        return PaginaResponse.de(pagina, choferMapper::toResponse);
    }

    @Override
    public ChoferResponse obtener(UUID id, UsuarioAutenticado autenticado) {
        Chofer chofer = buscar(id);
        autorizacion.verificarGestion(autenticado, chofer.getIdSucursal(), "chofer");
        return choferMapper.toResponse(chofer);
    }

    @Override
    @Transactional
    public ChoferResponse actualizar(UUID id, ActualizarChoferRequest request, UsuarioAutenticado autenticado) {
        Chofer chofer = buscar(id);
        autorizacion.verificarGestion(autenticado, chofer.getIdSucursal(), "chofer");
        autorizacion.verificarVersion(request.version(), chofer.getVersion());
        String licencia = request.numeroLicencia().trim();
        if (choferRepository.existsByNumeroLicenciaAndIdNot(licencia, id)) {
            throw new LicenciaDuplicadaException(licencia);
        }
        if (request.idUsuario() != null && choferRepository.existsByIdUsuarioAndIdNot(request.idUsuario(), id)) {
            throw new UsuarioYaEnlazadoException(request.idUsuario());
        }
        choferMapper.actualizar(chofer, request);
        chofer.setNumeroLicencia(licencia);
        Chofer guardado = choferRepository.saveAndFlush(chofer);
        eventos.choferActualizado(guardado);
        return choferMapper.toResponse(guardado);
    }

    @Override
    @Transactional
    public ChoferResponse cambiarActivo(UUID id, CambiarActivoRequest request, UsuarioAutenticado autenticado) {
        Chofer chofer = buscar(id);
        autorizacion.verificarGestion(autenticado, chofer.getIdSucursal(), "chofer");
        autorizacion.verificarVersion(request.version(), chofer.getVersion());
        boolean activo = request.activo();
        if (Boolean.valueOf(activo).equals(chofer.getActivo())) {
            return choferMapper.toResponse(chofer);   // sin cambio: no se publica evento
        }
        chofer.setActivo(activo);
        Chofer guardado = choferRepository.saveAndFlush(chofer);
        eventos.choferActualizado(guardado);
        return choferMapper.toResponse(guardado);
    }

    @Override
    @Transactional
    public ChoferResponse guardarFoto(UUID id, MultipartFile archivo, UsuarioAutenticado autenticado) {
        Chofer chofer = buscar(id);
        autorizacion.verificarGestion(autenticado, chofer.getIdSucursal(), "chofer");
        chofer.setFotoUrl(fotoService.guardar(FotoService.Tipo.CHOFERES, id, archivo));
        // Fuerza una versión nueva aunque la clave no cambie (misma extensión): la fotoUrl que ve el
        // frontend lleva ?v={version} y así el navegador no muestra la foto anterior desde su caché.
        chofer.setActualizadoEn(OffsetDateTime.now());
        return choferMapper.toResponse(choferRepository.saveAndFlush(chofer));
    }

    @Override
    public FotoService.FotoLeida leerFoto(UUID id, UsuarioAutenticado autenticado) {
        Chofer chofer = buscar(id);
        autorizacion.verificarGestion(autenticado, chofer.getIdSucursal(), "chofer");
        return fotoService.leerGuardada(FotoService.Tipo.CHOFERES, chofer.getFotoUrl())
                .orElseThrow(FotoNoEncontradaException::new);
    }

    private Chofer buscar(UUID id) {
        return choferRepository.findById(id).orElseThrow(() -> new ChoferNoEncontradoException(id));
    }

}
