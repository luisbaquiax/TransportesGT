package org.transportsgt.reportingservice.services.comun;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.reportingservice.config.security.UsuarioAutenticado;
import org.transportsgt.reportingservice.exception.BusNoEncontradoException;
import org.transportsgt.reportingservice.exception.RutaNoEncontradaException;
import org.transportsgt.reportingservice.exception.SinPermisoSucursalException;
import org.transportsgt.reportingservice.exception.SucursalNoEncontradaException;
import org.transportsgt.reportingservice.exception.SucursalRequeridaException;
import org.transportsgt.reportingservice.models.RepBus;
import org.transportsgt.reportingservice.models.RepRuta;
import org.transportsgt.reportingservice.models.RepSucursal;
import org.transportsgt.reportingservice.repositories.RepBusRepository;
import org.transportsgt.reportingservice.repositories.RepRutaRepository;
import org.transportsgt.reportingservice.repositories.RepSucursalRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Regla de sucursal: el ADMIN_SUCURSAL siempre ve la sucursal de su token (403 si pide otra explícitamente);
 * el ADMIN_SISTEMA indica la sucursal con {@code idSucursal}.
 */
@Component
@RequiredArgsConstructor
public class AlcanceSucursal {

    private final RepSucursalRepository sucursalRepository;
    private final RepBusRepository busRepository;
    private final RepRutaRepository rutaRepository;

    /** Reportes de sucursal (7 a 12): obligatoria para el ADMIN_SISTEMA, la del token para el ADMIN_SUCURSAL. */
    public RepSucursal sucursalDelReporte(UsuarioAutenticado usuario, UUID idSucursalPedida) {
        UUID idSucursal = usuario.esAdminSistema() ? idSucursalPedida : sucursalDelToken(usuario, idSucursalPedida);
        if (idSucursal == null) {
            throw new SucursalRequeridaException();
        }
        return buscarSucursal(idSucursal);
    }

    /** Filtro opcional de sucursal (reportes del sistema). */
    public Optional<RepSucursal> sucursalOpcional(UUID idSucursal) {
        return idSucursal == null ? Optional.empty() : Optional.of(buscarSucursal(idSucursal));
    }

    /** Catálogos: el ADMIN_SISTEMA puede omitir la sucursal (todas); el ADMIN_SUCURSAL solo ve la suya. */
    public UUID sucursalDelCatalogo(UsuarioAutenticado usuario, UUID idSucursalPedida) {
        return usuario.esAdminSistema() ? idSucursalPedida : sucursalDelToken(usuario, idSucursalPedida);
    }

    public RepSucursal buscarSucursal(UUID idSucursal) {
        return sucursalRepository.findById(idSucursal)
                .orElseThrow(() -> new SucursalNoEncontradaException(idSucursal));
    }

    /** Filtro opcional de bus; si se indica sucursal, el bus debe pertenecer a ella. */
    public Optional<RepBus> busDeSucursal(UUID idBus, UUID idSucursal) {
        if (idBus == null) {
            return Optional.empty();
        }
        RepBus bus = busRepository.findById(idBus).orElseThrow(() -> new BusNoEncontradoException(idBus));
        if (idSucursal != null && !idSucursal.equals(bus.getIdSucursal())) {
            throw new SinPermisoSucursalException();
        }
        return Optional.of(bus);
    }

    /** Filtro opcional de ruta; la ruta debe salir de la sucursal indicada. */
    public Optional<RepRuta> rutaDeSucursal(UUID idRuta, UUID idSucursal) {
        if (idRuta == null) {
            return Optional.empty();
        }
        RepRuta ruta = rutaRepository.findById(idRuta).orElseThrow(() -> new RutaNoEncontradaException(idRuta));
        if (idSucursal != null && !idSucursal.equals(ruta.getIdSucursalOrigen())) {
            throw new SinPermisoSucursalException();
        }
        return Optional.of(ruta);
    }

    private UUID sucursalDelToken(UsuarioAutenticado usuario, UUID idSucursalPedida) {
        if (idSucursalPedida != null && !usuario.perteneceASucursal(idSucursalPedida)) {
            throw new SinPermisoSucursalException();
        }
        return usuario.idSucursal();
    }
}
