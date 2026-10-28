package org.transportsgt.operationalcostsservice.eventos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.payload.viajes.DatosLlegada;
import org.transportsgt.eventos.payload.viajes.TipoViaje;
import org.transportsgt.operationalcostsservice.dominio.CalculadoraCostosViaje;
import org.transportsgt.operationalcostsservice.exception.ReglaIntegridadException;
import org.transportsgt.operationalcostsservice.mappers.CostosMapper;
import org.transportsgt.operationalcostsservice.models.GastoCombustible;
import org.transportsgt.operationalcostsservice.models.PagoChofer;
import org.transportsgt.operationalcostsservice.models.RegistroDepreciacion;
import org.transportsgt.operationalcostsservice.repositories.GastoCombustibleRepository;
import org.transportsgt.operationalcostsservice.repositories.PagoChoferRepository;
import org.transportsgt.operationalcostsservice.repositories.RegistroDepreciacionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManejadorLlegadaRegistradaTest {

    @Mock GastoCombustibleRepository combustibleRepository;
    @Mock RegistroDepreciacionRepository depreciacionRepository;
    @Mock PagoChoferRepository pagoChoferRepository;
    @Mock CalculadoraCostosViaje calculadora;
    @Mock CostosMapper mapper;
    @Mock EventosCostos eventos;

    private ManejadorLlegadaRegistrada manejador() {
        return new ManejadorLlegadaRegistrada(combustibleRepository, depreciacionRepository,
                pagoChoferRepository, calculadora, mapper, eventos);
    }

    private DatosLlegada datos(UUID idViaje) {
        OffsetDateTime llegada = OffsetDateTime.of(2026, 10, 25, 10, 41, 0, 0, ZoneOffset.UTC);
        return new DatosLlegada(idViaje, TipoViaje.REGULAR, null, null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("150.00"),
                llegada.minusHours(4), llegada,
                new BigDecimal("123450"), new BigDecimal("123655.5"),
                new BigDecimal("205.5"), new BigDecimal("420.00"),
                UUID.randomUUID());
    }

    @SuppressWarnings("unchecked")
    private SobreEvento<DatosLlegada> sobre(DatosLlegada d) {
        SobreEvento<DatosLlegada> evento = org.mockito.Mockito.mock(SobreEvento.class);
        when(evento.datos()).thenReturn(d);
        return evento;
    }

    private void persistidorAsignaIds() {
        when(combustibleRepository.saveAndFlush(any())).thenAnswer(i -> {
            GastoCombustible g = i.getArgument(0);
            g.setId(UUID.randomUUID());
            return g;
        });
        when(depreciacionRepository.saveAndFlush(any())).thenAnswer(i -> {
            RegistroDepreciacion r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
        when(pagoChoferRepository.saveAndFlush(any())).thenAnswer(i -> {
            PagoChofer p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
    }

    @Test
    void idempotenteSiYaExiste() {
        UUID idViaje = UUID.randomUUID();
        SobreEvento<DatosLlegada> evento = sobre(datos(idViaje));
        when(combustibleRepository.existsByIdViaje(idViaje)).thenReturn(true);

        manejador().manejar(evento);

        verify(combustibleRepository, never()).saveAndFlush(any());
        verifyNoInteractions(calculadora, mapper, eventos);
    }

    @Test
    void calculaPersisteYPublicaLosTresCostosEnOrden() {
        UUID idViaje = UUID.randomUUID();
        SobreEvento<DatosLlegada> evento = sobre(datos(idViaje));
        var comb = GastoCombustible.builder().id(UUID.randomUUID()).idViaje(idViaje)
                .monto(new BigDecimal("420.00")).fechaGasto(LocalDate.of(2026, 10, 25)).build();
        var dep = RegistroDepreciacion.builder().id(UUID.randomUUID()).idViaje(idViaje)
                .monto(new BigDecimal("411.00")).build();
        var pago = PagoChofer.builder().id(UUID.randomUUID()).idViaje(idViaje)
                .monto(new BigDecimal("150.00")).build();

        when(combustibleRepository.existsByIdViaje(idViaje)).thenReturn(false);
        when(calculadora.combustible(any(), any())).thenReturn(comb);
        when(calculadora.depreciacion(any(), any())).thenReturn(dep);
        when(calculadora.pagoChofer(any(), any())).thenReturn(pago);
        persistidorAsignaIds();

        manejador().manejar(evento);

        InOrder orden = inOrder(calculadora, combustibleRepository, depreciacionRepository, pagoChoferRepository);
        orden.verify(calculadora).combustible(any(), any());
        orden.verify(combustibleRepository).saveAndFlush(comb);
        orden.verify(calculadora).depreciacion(any(), any());
        orden.verify(depreciacionRepository).saveAndFlush(dep);
        orden.verify(calculadora).pagoChofer(any(), any());
        orden.verify(pagoChoferRepository).saveAndFlush(pago);

        verify(eventos).gastoCombustibleRegistrado(any());
        verify(eventos).depreciacionCalculada(any());
        verify(eventos).pagoChoferRegistrado(any());
    }

    @Test
    void sinTarifaNoPublicaNiPersisteLosPosteriores() {
        UUID idViaje = UUID.randomUUID();
        SobreEvento<DatosLlegada> evento = sobre(datos(idViaje));
        var comb = GastoCombustible.builder().id(UUID.randomUUID()).idViaje(idViaje)
                .monto(new BigDecimal("420.00")).build();

        when(combustibleRepository.existsByIdViaje(idViaje)).thenReturn(false);
        when(calculadora.combustible(any(), any())).thenReturn(comb);
        when(combustibleRepository.saveAndFlush(any())).thenReturn(comb);
        when(calculadora.depreciacion(any(), any())).thenThrow(new ReglaIntegridadException(
                HttpStatus.UNPROCESSABLE_ENTITY, "SIN_TARIFA", "No hay tarifa"));

        assertThatThrownBy(() -> manejador().manejar(evento))
                .isInstanceOf(ReglaIntegridadException.class);

        verify(depreciacionRepository, never()).saveAndFlush(any());
        verify(pagoChoferRepository, never()).saveAndFlush(any());
        verify(eventos, never()).depreciacionCalculada(any());
        verify(eventos, never()).pagoChoferRegistrado(any());
    }
}
