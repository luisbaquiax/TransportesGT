package org.transportsgt.fleetservice.services.foto.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.transportsgt.fleetservice.almacenamiento.AlmacenamientoFotos;
import org.transportsgt.fleetservice.config.security.UsuarioAutenticado;
import org.transportsgt.fleetservice.dto.response.BusResponse;
import org.transportsgt.fleetservice.dto.response.ChoferResponse;
import org.transportsgt.fleetservice.exception.FotoNoEncontradaException;
import org.transportsgt.fleetservice.mappers.BusMapper;
import org.transportsgt.fleetservice.mappers.ChoferMapper;
import org.transportsgt.fleetservice.models.Bus;
import org.transportsgt.fleetservice.models.Chofer;
import org.transportsgt.fleetservice.repositories.BusRepository;
import org.transportsgt.fleetservice.repositories.ChoferRepository;
import org.transportsgt.fleetservice.services.autorizacion.AutorizacionSucursal;
import org.transportsgt.fleetservice.services.comun.BuscadorFlota;
import org.transportsgt.fleetservice.services.foto.FotoLeida;
import org.transportsgt.fleetservice.services.foto.FotosFlotaService;
import org.transportsgt.fleetservice.services.foto.TipoFoto;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Valida la imagen y delega el guardado en el {@link AlmacenamientoFotos} activo. La clave siempre es
 * {@code {buses|choferes}/{id}.{ext}}; la referencia que queda en foto_url nunca se expone.
 */
@Service
@RequiredArgsConstructor
public class FotosFlotaServiceImpl implements FotosFlotaService {

    /**
     * Nombres de archivo aceptados: nada de rutas ni caracteres raros.
     */
    private static final Pattern NOMBRE_SEGURO = Pattern.compile("^[A-Za-z0-9._-]{1,100}$");

    private final AlmacenamientoFotos almacenamiento;
    private final ValidadorFoto validador;
    private final FormatosFoto formatos;
    private final BuscadorFlota buscador;
    private final AutorizacionSucursal autorizacion;
    private final BusRepository busRepository;
    private final ChoferRepository choferRepository;
    private final BusMapper busMapper;
    private final ChoferMapper choferMapper;
    private final Clock clock;

    @Override
    @Transactional
    public BusResponse guardarFotoBus(UUID id, MultipartFile archivo, UsuarioAutenticado autenticado) {
        Bus bus = buscador.bus(id);
        autorizacion.verificarGestion(autenticado, bus.getIdSucursal(), "bus");
        bus.setFotoUrl(guardar(TipoFoto.BUSES, id, archivo));
        // Fuerza una versión nueva aunque la clave no cambie (misma extensión): la fotoUrl que ve el
        // frontend lleva ?v={version} y así el navegador no muestra la foto anterior desde su caché.
        bus.setActualizadoEn(OffsetDateTime.now(clock));
        return busMapper.toResponse(busRepository.saveAndFlush(bus));
    }

    @Override
    @Transactional(readOnly = true)
    public FotoLeida leerFotoBus(UUID id, UsuarioAutenticado autenticado) {
        Bus bus = buscador.bus(id);
        autorizacion.verificarGestion(autenticado, bus.getIdSucursal(), "bus");
        return leerGuardada(TipoFoto.BUSES, bus.getFotoUrl());
    }

    @Override
    @Transactional
    public ChoferResponse guardarFotoChofer(UUID id, MultipartFile archivo, UsuarioAutenticado autenticado) {
        Chofer chofer = buscador.chofer(id);
        autorizacion.verificarGestion(autenticado, chofer.getIdSucursal(), "chofer");
        chofer.setFotoUrl(guardar(TipoFoto.CHOFERES, id, archivo));
        chofer.setActualizadoEn(OffsetDateTime.now(clock));
        return choferMapper.toResponse(choferRepository.saveAndFlush(chofer));
    }

    @Override
    @Transactional(readOnly = true)
    public FotoLeida leerFotoChofer(UUID id, UsuarioAutenticado autenticado) {
        Chofer chofer = buscador.chofer(id);
        autorizacion.verificarGestion(autenticado, chofer.getIdSucursal(), "chofer");
        return leerGuardada(TipoFoto.CHOFERES, chofer.getFotoUrl());
    }

    @Override
    public Optional<FotoLeida> leerPorNombre(TipoFoto tipo, String nombre) {
        if (nombre == null || !NOMBRE_SEGURO.matcher(nombre).matches()) {
            return Optional.empty();
        }
        return almacenamiento.leer(tipo.carpeta() + "/" + nombre)
                .map(contenido -> new FotoLeida(contenido, formatos.tipoDe(nombre)));
    }

    private String guardar(TipoFoto tipo, UUID id, MultipartFile archivo) {
        String extension = validador.validar(archivo);
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la imagen enviada", e);
        }
        return almacenamiento.guardar(tipo.carpeta() + "/" + id + "." + extension, contenido,
                formatos.normalizar(archivo.getContentType()));
    }

    /**
     * foto_url puede traer la URL completa (registros anteriores) o la clave: basta el nombre del archivo.
     */
    private FotoLeida leerGuardada(TipoFoto tipo, String fotoGuardada) {
        if (fotoGuardada == null || fotoGuardada.isBlank()) {
            throw new FotoNoEncontradaException();
        }
        String nombre = fotoGuardada.substring(fotoGuardada.lastIndexOf('/') + 1);
        return leerPorNombre(tipo, nombre).orElseThrow(FotoNoEncontradaException::new);
    }
}
