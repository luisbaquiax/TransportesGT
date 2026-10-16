package org.transportsgt.customerwalletservice.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.customerwalletservice.config.security.UsuarioAutenticado;
import org.transportsgt.customerwalletservice.dto.request.ActualizarPerfilRequest;
import org.transportsgt.customerwalletservice.dto.response.ClienteBusquedaResponse;
import org.transportsgt.customerwalletservice.dto.response.ClienteResponse;
import org.transportsgt.customerwalletservice.eventos.EventosCartera;
import org.transportsgt.customerwalletservice.exception.ClienteNoEncontradoException;
import org.transportsgt.customerwalletservice.exception.DpiDuplicadoException;
import org.transportsgt.customerwalletservice.exception.NitDuplicadoException;
import org.transportsgt.customerwalletservice.exception.NitInvalidoException;
import org.transportsgt.customerwalletservice.mappers.ClienteMapper;
import org.transportsgt.customerwalletservice.models.Cartera;
import org.transportsgt.customerwalletservice.models.Cliente;
import org.transportsgt.customerwalletservice.repositories.CarteraRepository;
import org.transportsgt.customerwalletservice.repositories.ClienteRepository;
import org.transportsgt.customerwalletservice.services.ClienteService;
import org.transportsgt.eventos.Nit;
import org.transportsgt.eventos.payload.DatosUsuario;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClienteServiceImpl implements ClienteService {

    private final ClienteRepository clienteRepository;
    private final CarteraRepository carteraRepository;
    private final ClienteMapper clienteMapper;
    private final EventosCartera eventos;

    @Override
    @Transactional
    public boolean aplicarUsuario(DatosUsuario datos, Long versionAgregado) {
        long version = versionAgregado == null ? 0L : versionAgregado;
        Optional<Cliente> existente = clienteRepository.findByIdUsuario(datos.idUsuario());
        if (existente.isEmpty()) {
            crearCliente(datos, version);
            return true;
        }
        Cliente cliente = existente.get();
        if (version <= cliente.getVersionUsuario()) {
            log.debug("Usuario {}: versión {} no es mayor que la aplicada ({}), se ignora",
                    datos.idUsuario(), version, cliente.getVersionUsuario());
            return false;
        }
        boolean cambiaPerfil = !Objects.equals(cliente.getNombreCompleto(), datos.nombreCompleto())
                || !Objects.equals(cliente.getActivo(), datos.activo());
        cliente.setNombreCompleto(datos.nombreCompleto());
        cliente.setCorreo(datos.correo());
        cliente.setActivo(datos.activo());
        cliente.setVersionUsuario(version);
        Cliente guardado = clienteRepository.saveAndFlush(cliente);
        if (cambiaPerfil) {
            eventos.perfilClienteActualizado(guardado);
        }
        return true;
    }

    private void crearCliente(DatosUsuario datos, long version) {
        Cliente cliente = clienteRepository.saveAndFlush(Cliente.builder()
                .idUsuario(datos.idUsuario())
                .nombreCompleto(datos.nombreCompleto())
                .correo(datos.correo())
                .activo(datos.activo())
                .versionUsuario(version)
                .build());
        carteraRepository.save(Cartera.builder().cliente(cliente).build());
        eventos.perfilClienteActualizado(cliente);
    }

    @Override
    public ClienteResponse obtenerPerfil(UsuarioAutenticado usuario) {
        Cliente cliente = buscarCliente(usuario);
        return clienteMapper.toResponse(cliente, saldoDe(cliente));
    }

    @Override
    @Transactional
    public ClienteResponse actualizarPerfil(UsuarioAutenticado usuario, ActualizarPerfilRequest request) {
        Cliente cliente = buscarCliente(usuario);
        String nit = Nit.normalizar(request.nit());
        if (nit != null && !Nit.esNitDeCliente(nit)) {
            throw new NitInvalidoException();
        }
        String dpi = textoONulo(request.dpi());
        if (nit != null && clienteRepository.existsByNitAndIdNot(nit, cliente.getId())) {
            throw new NitDuplicadoException();
        }
        if (dpi != null && clienteRepository.existsByDpiAndIdNot(dpi, cliente.getId())) {
            throw new DpiDuplicadoException();
        }
        boolean cambiaNit = !Objects.equals(cliente.getNit(), nit);
        cliente.setNit(nit);
        cliente.setDpi(dpi);
        cliente.setTelefono(textoONulo(request.telefono()));
        cliente.setDireccion(textoONulo(request.direccion()));
        Cliente guardado = clienteRepository.saveAndFlush(cliente);
        if (cambiaNit) {
            eventos.perfilClienteActualizado(guardado);
        }
        return clienteMapper.toResponse(guardado, saldoDe(guardado));
    }

    @Override
    public ClienteBusquedaResponse buscarPorNit(String nit) {
        String normalizado = Nit.normalizar(nit);
        if (normalizado == null) {
            throw new ClienteNoEncontradoException();
        }
        return clienteRepository.findByNit(normalizado)
                .map(clienteMapper::toBusqueda)
                .orElseThrow(ClienteNoEncontradoException::new);
    }

    private Cliente buscarCliente(UsuarioAutenticado usuario) {
        return clienteRepository.findByIdUsuario(usuario.id()).orElseThrow(ClienteNoEncontradoException::new);
    }

    private BigDecimal saldoDe(Cliente cliente) {
        return carteraRepository.findByClienteIdUsuario(cliente.getIdUsuario())
                .map(Cartera::getSaldo)
                .orElse(BigDecimal.ZERO);
    }

    private String textoONulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
