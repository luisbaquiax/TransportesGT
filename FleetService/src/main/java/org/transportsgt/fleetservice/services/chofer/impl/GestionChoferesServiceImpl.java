package org.transportsgt.fleetservice.services.chofer.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.fleetservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.fleetservice.dto.request.ActualizarChoferRequest;
import org.transportsgt.fleetservice.dto.request.CambiarActivoRequest;
import org.transportsgt.fleetservice.dto.request.CrearChoferRequest;
import org.transportsgt.fleetservice.dto.response.ChoferResponse;
import org.transportsgt.fleetservice.eventos.EventosFlota;
import org.transportsgt.fleetservice.mappers.ChoferMapper;
import org.transportsgt.fleetservice.models.Chofer;
import org.transportsgt.fleetservice.repositories.ChoferRepository;
import org.transportsgt.fleetservice.services.autorizacion.AutorizacionSucursal;
import org.transportsgt.fleetservice.services.chofer.GestionChoferesService;
import org.transportsgt.fleetservice.services.comun.BuscadorFlota;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GestionChoferesServiceImpl implements GestionChoferesService {

    private final ChoferRepository choferRepository;
    private final BuscadorFlota buscador;
    private final AutorizacionSucursal autorizacion;
    private final ValidadorChofer validador;
    private final ChoferMapper choferMapper;
    private final EventosFlota eventos;

    @Override
    @Transactional
    public ChoferResponse crear(CrearChoferRequest request, UsuarioAutenticado autenticado) {
        autorizacion.validarSucursalGestionada(autenticado.idSucursal());
        String licencia = request.numeroLicencia().trim();
        validador.licenciaDisponible(licencia);
        validador.usuarioDisponible(request.idUsuario());
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
    @Transactional
    public ChoferResponse actualizar(UUID id, ActualizarChoferRequest request, UsuarioAutenticado autenticado) {
        Chofer chofer = cargarParaGestion(id, request.version(), autenticado);
        String licencia = request.numeroLicencia().trim();
        validador.licenciaDisponibleParaOtro(licencia, id);
        validador.usuarioDisponibleParaOtro(request.idUsuario(), id);
        choferMapper.actualizar(chofer, request);
        chofer.setNumeroLicencia(licencia);
        return publicarCambio(chofer);
    }

    @Override
    @Transactional
    public ChoferResponse cambiarActivo(UUID id, CambiarActivoRequest request, UsuarioAutenticado autenticado) {
        Chofer chofer = cargarParaGestion(id, request.version(), autenticado);
        boolean activo = request.activo();
        if (Boolean.valueOf(activo).equals(chofer.getActivo())) {
            return choferMapper.toResponse(chofer);   // sin cambio: no se publica evento
        }
        chofer.setActivo(activo);
        return publicarCambio(chofer);
    }

    private Chofer cargarParaGestion(UUID id, Long version, UsuarioAutenticado autenticado) {
        Chofer chofer = buscador.chofer(id);
        autorizacion.verificarGestion(autenticado, chofer.getIdSucursal(), "chofer");
        autorizacion.verificarVersion(version, chofer.getVersion());
        return chofer;
    }

    /** Guarda con flush para que ChoferActualizado lleve la versión ya incrementada. */
    private ChoferResponse publicarCambio(Chofer chofer) {
        Chofer guardado = choferRepository.saveAndFlush(chofer);
        eventos.choferActualizado(guardado);
        return choferMapper.toResponse(guardado);
    }
}
