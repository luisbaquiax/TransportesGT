package org.transportsgt.tripservice.services.ruta.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.tripservice.config.security.UsuarioAutenticado;
import org.transportsgt.tripservice.dto.request.Requests;
import org.transportsgt.tripservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.tripservice.exception.ReglaNegocioException;
import org.transportsgt.tripservice.models.Ruta;
import org.transportsgt.tripservice.repositories.RutaRepository;
import org.transportsgt.tripservice.repositories.SucursalRefRepository;

import java.util.UUID;

/** Reglas de una ruta: quién la gestiona, origen distinto del destino, sucursales activas y sin duplicados. */
@Component
@RequiredArgsConstructor
class ValidadorRuta {

    private final RutaRepository rutaRepository;
    private final SucursalRefRepository sucursalRefRepository;

    /** @param accion texto del mensaje, p. ej. "crear la ruta" */
    void verificarGestion(UsuarioAutenticado usuario, UUID idSucursalOrigen, String accion) {
        if (!usuario.perteneceASucursal(idSucursalOrigen)) {
            throw new OperacionNoAutorizadaException(
                    "Solo el administrador de la sucursal de origen puede " + accion);
        }
    }

    void validarNueva(Requests.RutaRequest request) {
        validarOrigenDistinto(request);
        if (!sucursalRefRepository.existsByIdAndActivaTrue(request.idSucursalOrigen())
                || !sucursalRefRepository.existsByIdAndActivaTrue(request.idSucursalDestino())) {
            throw new ReglaNegocioException("SUCURSAL_NO_ENCONTRADA",
                    "Ambas sucursales deben existir y estar activas");
        }
        if (rutaRepository.existsByIdSucursalOrigenAndIdSucursalDestino(
                request.idSucursalOrigen(), request.idSucursalDestino())) {
            throw new ReglaNegocioException("RUTA_DUPLICADA",
                    "Ya existe una ruta entre esas dos sucursales");
        }
    }

    void validarCambio(Ruta ruta, Requests.RutaRequest request) {
        if (!request.idSucursalOrigen().equals(ruta.getIdSucursalOrigen())) {
            throw new ReglaNegocioException("RUTA_INVALIDA", "No se puede cambiar la sucursal de origen de la ruta");
        }
        validarOrigenDistinto(request);
        if (rutaRepository.existsByIdSucursalOrigenAndIdSucursalDestinoAndIdNot(
                request.idSucursalOrigen(), request.idSucursalDestino(), ruta.getId())) {
            throw new ReglaNegocioException("RUTA_DUPLICADA",
                    "Ya existe otra ruta entre esas dos sucursales");
        }
    }

    private void validarOrigenDistinto(Requests.RutaRequest request) {
        if (request.idSucursalOrigen().equals(request.idSucursalDestino())) {
            throw new ReglaNegocioException("RUTA_INVALIDA", "El origen y el destino deben ser distintos");
        }
    }
}
