package org.transportsgt.reportingservice.services.catalogos.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.reportingservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.reportingservice.dto.response.catalogos.OpcionCatalogo;
import org.transportsgt.reportingservice.repositories.catalogos.CatalogoRepository;
import org.transportsgt.reportingservice.repositories.catalogos.OpcionConsulta;
import org.transportsgt.reportingservice.services.catalogos.CatalogoService;
import org.transportsgt.reportingservice.services.comun.AlcanceSucursal;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogoServiceImpl implements CatalogoService {

    private final CatalogoRepository repository;
    private final AlcanceSucursal alcance;

    @Override
    public List<OpcionCatalogo> sucursales() {
        return aOpciones(repository.sucursales());
    }

    @Override
    public List<OpcionCatalogo> rutas(UsuarioAutenticado usuario, UUID idSucursal) {
        return aOpciones(repository.rutas(alcance.sucursalDelCatalogo(usuario, idSucursal)));
    }

    @Override
    public List<OpcionCatalogo> buses(UsuarioAutenticado usuario, UUID idSucursal) {
        return aOpciones(repository.buses(alcance.sucursalDelCatalogo(usuario, idSucursal)));
    }

    private List<OpcionCatalogo> aOpciones(List<OpcionConsulta> consulta) {
        return consulta.stream()
                .map(c -> new OpcionCatalogo(c.getId(), c.getNombre(), Boolean.TRUE.equals(c.getActivo())))
                .toList();
    }
}
