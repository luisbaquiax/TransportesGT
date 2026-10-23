package org.transportsgt.privaterentalservice.services.consulta.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.transportsgt.privaterentalservice.dto.request.FiltroAlquileres;
import org.transportsgt.privaterentalservice.dto.response.PaginaResponse;
import org.transportsgt.privaterentalservice.exception.AlquilerNoEncontradoException;
import org.transportsgt.privaterentalservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.privaterentalservice.mappers.AlquilerMapper;
import org.transportsgt.privaterentalservice.mappers.FabricaPaginas;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;
import org.transportsgt.privaterentalservice.repositories.AlquilerRepository;
import org.transportsgt.privaterentalservice.repositories.AlquilerSpecs;
import org.transportsgt.privaterentalservice.repositories.ClienteRefRepository;
import org.transportsgt.privaterentalservice.repositories.CotizacionAlquilerRepository;
import org.transportsgt.privaterentalservice.services.autorizacion.AutorizacionAlquiler;
import org.transportsgt.privaterentalservice.services.comun.BuscadorAlquileres;
import org.transportsgt.privaterentalservice.services.comun.NormalizadorNit;
import org.transportsgt.privaterentalservice.soporte.DatosPrueba;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultaAlquileresServiceImplTest {

    @Mock
    private AlquilerRepository alquilerRepository;
    @Mock
    private CotizacionAlquilerRepository cotizacionRepository;
    @Mock
    private ClienteRefRepository clienteRepository;
    @Mock
    private AlquilerMapper mapper;

    private final DatosPrueba datos = new DatosPrueba();
    private ConsultaAlquileresServiceImpl servicio;

    @BeforeEach
    void setUp() {
        servicio = new ConsultaAlquileresServiceImpl(alquilerRepository, cotizacionRepository,
                new AlquilerSpecs(datos.calendario), new BuscadorAlquileres(alquilerRepository),
                new AutorizacionAlquiler(clienteRepository, new NormalizadorNit()), mapper, new FabricaPaginas());
    }

    private Alquiler existente(Alquiler a) {
        when(alquilerRepository.findById(a.getId())).thenReturn(Optional.of(a));
        return a;
    }

    private void detalleMapeado(Alquiler a) {
        when(cotizacionRepository.findAllByAlquilerIdOrderByCreadoEn(a.getId())).thenReturn(List.of());
        when(mapper.toDetalle(eq(a), any())).thenAnswer(inv -> datos.respuesta(a));
    }

    @Test
    void detalleDelClientePropio() {
        Alquiler a = existente(datos.alquiler(EstadoAlquiler.SOLICITADO));
        detalleMapeado(a);
        assertEquals(a.getId(), servicio.detalle(a.getId(), datos.cliente).id());
    }

    @Test
    void detalleDeOtroClienteEs403() {
        Alquiler a = existente(datos.alquiler(EstadoAlquiler.SOLICITADO));
        assertThrows(OperacionNoAutorizadaException.class, () -> servicio.detalle(a.getId(), datos.otroCliente));
    }

    @Test
    void detalleDeMostradorConElNitDelPerfil() {
        Alquiler a = existente(datos.alquilerMostrador(EstadoAlquiler.CONFIRMADO, "1234567K"));
        when(clienteRepository.findById(datos.idCliente)).thenReturn(Optional.of(datos.perfil("1234567-k")));
        detalleMapeado(a);
        assertEquals(a.getId(), servicio.detalle(a.getId(), datos.cliente).id());
    }

    @Test
    void consumidorFinalNoDaAccesoAlDetalle() {
        Alquiler a = existente(datos.alquilerMostrador(EstadoAlquiler.CONFIRMADO, "CF"));
        assertThrows(OperacionNoAutorizadaException.class, () -> servicio.detalle(a.getId(), datos.cliente));
    }

    @Test
    void detalleInexistenteEs404() {
        UUID id = UUID.randomUUID();
        when(alquilerRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(AlquilerNoEncontradoException.class, () -> servicio.detalle(id, datos.adminSistema));
    }

    @Test
    void miosUsaElNitNormalizadoDelPerfil() {
        when(clienteRepository.findById(datos.idCliente)).thenReturn(Optional.of(datos.perfil("1234567-8")));
        when(alquilerRepository.findHistorial(eq(datos.idCliente), eq("12345678"), any()))
                .thenReturn(new PageImpl<>(List.of()));
        PaginaResponse<?> pagina = servicio.mios(datos.cliente, PageRequest.of(0, 20));
        assertEquals(0, pagina.totalElementos());
    }

    @Test
    void miosSinPerfilBuscaSoloPorUsuario() {
        when(clienteRepository.findById(datos.idCliente)).thenReturn(Optional.empty());
        when(alquilerRepository.findHistorial(eq(datos.idCliente), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of()));
        servicio.mios(datos.cliente, PageRequest.of(0, 20));
        verify(alquilerRepository).findHistorial(eq(datos.idCliente), eq(null), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void listarDevuelvePaginaMapeada() {
        Alquiler a = datos.alquiler(EstadoAlquiler.CONFIRMADO);
        PageRequest pedido = PageRequest.of(0, 20);
        when(alquilerRepository.findAll(any(Specification.class), eq(pedido)))
                .thenReturn(new PageImpl<>(List.of(a), pedido, 1));
        when(mapper.toResponseList(List.of(a))).thenReturn(List.of(datos.respuesta(a)));

        PaginaResponse<?> pagina = servicio.listar(new FiltroAlquileres(null, null, null, true),
                datos.adminSucursal, pedido);

        assertEquals(1, pagina.totalElementos());
        assertEquals(1, pagina.contenido().size());
    }
}
