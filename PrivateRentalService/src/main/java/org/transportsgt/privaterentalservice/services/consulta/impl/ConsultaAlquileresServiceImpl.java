package org.transportsgt.privaterentalservice.services.consulta.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.privaterentalservice.config.security.UsuarioAutenticado;
import org.transportsgt.privaterentalservice.dto.request.FiltroAlquileres;
import org.transportsgt.privaterentalservice.dto.response.AlquilerResponse;
import org.transportsgt.privaterentalservice.dto.response.PaginaResponse;
import org.transportsgt.privaterentalservice.mappers.AlquilerMapper;
import org.transportsgt.privaterentalservice.mappers.FabricaPaginas;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.repositories.AlquilerRepository;
import org.transportsgt.privaterentalservice.repositories.AlquilerSpecs;
import org.transportsgt.privaterentalservice.repositories.CotizacionAlquilerRepository;
import org.transportsgt.privaterentalservice.services.autorizacion.AutorizacionAlquiler;
import org.transportsgt.privaterentalservice.services.comun.BuscadorAlquileres;
import org.transportsgt.privaterentalservice.services.consulta.ConsultaAlquileresService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultaAlquileresServiceImpl implements ConsultaAlquileresService {

    private final AlquilerRepository alquilerRepository;
    private final CotizacionAlquilerRepository cotizacionRepository;
    private final AlquilerSpecs specs;
    private final BuscadorAlquileres buscador;
    private final AutorizacionAlquiler autorizacion;
    private final AlquilerMapper mapper;
    private final FabricaPaginas paginas;

    @Override
    public AlquilerResponse detalle(UUID id, UsuarioAutenticado usuario) {
        Alquiler alquiler = buscador.porId(id);
        autorizacion.verificarLectura(alquiler, usuario);
        return mapper.toDetalle(alquiler, cotizacionRepository.findAllByAlquilerIdOrderByCreadoEn(id));
    }

    @Override
    public PaginaResponse<AlquilerResponse> listar(FiltroAlquileres filtro, UsuarioAutenticado usuario,
                                                   Pageable pageable) {
        Page<Alquiler> pagina = alquilerRepository.findAll(specs.filtrar(filtro, usuario), pageable);
        return paginas.de(pagina, mapper.toResponseList(pagina.getContent()));
    }

    @Override
    public PaginaResponse<AlquilerResponse> mios(UsuarioAutenticado usuario, Pageable pageable) {
        String nit = autorizacion.nitDelCliente(usuario.id());
        Page<Alquiler> pagina = alquilerRepository.findHistorial(usuario.id(), nit, pageable);
        return paginas.de(pagina, mapper.toResponseList(pagina.getContent()));
    }
}
