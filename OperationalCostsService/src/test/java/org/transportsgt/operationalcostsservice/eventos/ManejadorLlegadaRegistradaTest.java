package org.transportsgt.operationalcostsservice.eventos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.payload.DatosLlegada;
import org.transportsgt.eventos.payload.TipoViaje;
import org.transportsgt.operationalcostsservice.exception.ReglaIntegridadException;
import org.transportsgt.operationalcostsservice.models.ConfiguracionDepreciacion;
import org.transportsgt.operationalcostsservice.models.GastoCombustible;
import org.transportsgt.operationalcostsservice.models.PagoChofer;
import org.transportsgt.operationalcostsservice.models.RegistroDepreciacion;
import org.transportsgt.operationalcostsservice.repositories.ConfiguracionDepreciacionRepository;
import org.transportsgt.operationalcostsservice.repositories.GastoCombustibleRepository;
import org.transportsgt.operationalcostsservice.repositories.PagoChoferRepository;
import org.transportsgt.operationalcostsservice.repositories.RegistroDepreciacionRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManejadorLlegadaRegistradaTest {

    @Mock GastoCombustibleRepository combustibleRepository;
    @Mock RegistroDepreciacionRepository depreciacionRepository;
    @Mock PagoChoferRepository pagoChoferRepository;
    @Mock ConfiguracionDepreciacionRepository tarifaRepository;
    @Mock EventosCostos eventos;

    private ManejadorLlegadaRegistrada manejador() {
        return new ManejadorLlegadaRegistrada(combustibleRepository, depreciacionRepository,
                pagoChoferRepository, tarifaRepository, eventos);
    }

    private DatosLlegada datos(TipoViaje tipo, UUID idViaje) {
        OffsetDateTime llegada = OffsetDateTime.of(2026, 10, 25, 10, 41, 0, 0, ZoneOffset.UTC);
        return new DatosLlegada(idViaje, tipo, null, null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("150.00"),
                llegada.minusHours(4), llegada,
                new BigDecimal("123450"), new BigDecimal("123655.5"),
                new BigDecimal("205.5"), new BigDecimal("420.00"),
                UUID.randomUUID());
    }

    @SuppressWarnings("unchecked")
    private SobreEvento<DatosLlegada> sobre(DatosLlegada d) {
        return org.mockito.Mockito.mock(SobreEvento.class);
    }

    @Test
    void calculaLosTresCostos() {
        UUID idViaje = UUID.randomUUID();
        DatosLlegada d = datos(TipoViaje.REGULAR, idViaje);
        SobreEvento<DatosLlegada> evento = sobre(d);
        when(evento.datos()).thenReturn(d);

        ConfiguracionDepreciacion tarifa = ConfiguracionDepreciacion.builder()
                .id(UUID.randomUUID()).montoPorKm(new BigDecimal("2.0000"))
                .vigenteDesde(java.time.LocalDate.of(2026, 1, 1))
                .creadoPor(UUID.randomUUID()).build();

        when(combustibleRepository.existsByIdViaje(idViaje)).thenReturn(false);
        when(combustibleRepository.saveAndFlush(any())).thenAnswer(i -> {
            GastoCombustible g = i.getArgument(0);
            g.setId(UUID.randomUUID());
            return g;
        });
        when(tarifaRepository.findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(any()))
                .thenReturn(Optional.of(tarifa));
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

        manejador().manejar(evento);

        // 205.5 × 2.00 = 411.00
        verify(depreciacionRepository).saveAndFlush(any());
        // 150.00 × 1.00 = 150.00
        verify(pagoChoferRepository).saveAndFlush(any());
        verify(eventos).gastoCombustibleRegistrado(any());
        verify(eventos).depreciacionCalculada(any());
        verify(eventos).pagoChoferRegistrado(any());
    }

    @Test
    void alquilerPrivadoUsa115() {
        UUID idViaje = UUID.randomUUID();
        DatosLlegada d = datos(TipoViaje.ALQUILER_PRIVADO, idViaje);
        SobreEvento<DatosLlegada> evento = sobre(d);
        when(evento.datos()).thenReturn(d);

        when(combustibleRepository.existsByIdViaje(idViaje)).thenReturn(false);
        when(combustibleRepository.saveAndFlush(any())).thenAnswer(i -> {
            GastoCombustible g = i.getArgument(0);
            g.setId(UUID.randomUUID());
            return g;
        });
        when(tarifaRepository.findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(any()))
                .thenReturn(Optional.of(ConfiguracionDepreciacion.builder()
                        .id(UUID.randomUUID()).montoPorKm(new BigDecimal("2.0000"))
                        .vigenteDesde(java.time.LocalDate.of(2026, 1, 1))
                        .creadoPor(UUID.randomUUID()).build()));
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

        manejador().manejar(evento);

        org.mockito.ArgumentCaptor<PagoChofer> captor = org.mockito.ArgumentCaptor.forClass(PagoChofer.class);
        verify(pagoChoferRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getMultiplicador()).isEqualByComparingTo("1.15");
        assertThat(captor.getValue().getMonto()).isEqualByComparingTo("172.50");
    }

    @Test
    void idempotenteSiYaExiste() {
        UUID idViaje = UUID.randomUUID();
        DatosLlegada d = datos(TipoViaje.REGULAR, idViaje);
        SobreEvento<DatosLlegada> evento = sobre(d);
        when(evento.datos()).thenReturn(d);
        when(combustibleRepository.existsByIdViaje(idViaje)).thenReturn(true);

        manejador().manejar(evento);

        verify(combustibleRepository, never()).saveAndFlush(any());
        verify(eventos, never()).gastoCombustibleRegistrado(any());
    }

    @Test
    void sinTarifaLanza422() {
        UUID idViaje = UUID.randomUUID();
        DatosLlegada d = datos(TipoViaje.REGULAR, idViaje);
        SobreEvento<DatosLlegada> evento = sobre(d);
        when(evento.datos()).thenReturn(d);

        when(combustibleRepository.existsByIdViaje(idViaje)).thenReturn(false);
        when(combustibleRepository.saveAndFlush(any())).thenAnswer(i -> {
            GastoCombustible g = i.getArgument(0);
            g.setId(UUID.randomUUID());
            return g;
        });
        when(tarifaRepository.findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> manejador().manejar(evento))
                .isInstanceOf(ReglaIntegridadException.class)
                .satisfies(ex -> {
                    ReglaIntegridadException e = (ReglaIntegridadException) ex;
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.getCodigo()).isEqualTo("SIN_TARIFA");
                });
    }
}
