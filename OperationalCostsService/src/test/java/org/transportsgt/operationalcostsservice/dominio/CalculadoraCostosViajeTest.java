package org.transportsgt.operationalcostsservice.dominio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.transportsgt.eventos.payload.DatosLlegada;
import org.transportsgt.eventos.payload.TipoViaje;
import org.transportsgt.operationalcostsservice.exception.ReglaIntegridadException;
import org.transportsgt.operationalcostsservice.models.ConfiguracionDepreciacion;
import org.transportsgt.operationalcostsservice.repositories.ConfiguracionDepreciacionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalculadoraCostosViajeTest {

    @Mock ConfiguracionDepreciacionRepository tarifaRepository;

    private final LocalDate fechaLlegada = LocalDate.of(2026, 10, 25);

    private CalculadoraCostosViaje calculadora() {
        return new CalculadoraCostosViaje(tarifaRepository);
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

    private ConfiguracionDepreciacion tarifa(BigDecimal montoPorKm) {
        return ConfiguracionDepreciacion.builder()
                .id(UUID.randomUUID()).montoPorKm(montoPorKm)
                .vigenteDesde(LocalDate.of(2026, 1, 1))
                .creadoPor(UUID.randomUUID()).build();
    }

    @Test
    void combustibleTomaElMontoDelEvento() {
        UUID idViaje = UUID.randomUUID();
        DatosLlegada d = datos(TipoViaje.REGULAR, idViaje);

        var g = calculadora().combustible(d, fechaLlegada);

        assertThat(g.getIdViaje()).isEqualTo(idViaje);
        assertThat(g.getIdBus()).isEqualTo(d.idBus());
        assertThat(g.getIdSucursal()).isEqualTo(d.idSucursalBus());
        assertThat(g.getMonto()).isEqualByComparingTo("420.00");
        assertThat(g.getFechaGasto()).isEqualTo(fechaLlegada);
    }

    @Test
    void depreciacionMultiplicaDistanciaPorTarifa() {
        when(tarifaRepository.findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(fechaLlegada))
                .thenReturn(Optional.of(tarifa(new BigDecimal("2.0000"))));

        var r = calculadora().depreciacion(datos(TipoViaje.REGULAR, UUID.randomUUID()), fechaLlegada);

        // 205.5 × 2.00 = 411.00
        assertThat(r.getDistanciaKm()).isEqualByComparingTo("205.5");
        assertThat(r.getMontoPorKm()).isEqualByComparingTo("2.0000");
        assertThat(r.getMonto()).isEqualByComparingTo("411.00");
        assertThat(r.getFechaCalculo()).isEqualTo(fechaLlegada);
    }

    @Test
    void sinTarifaLanza422() {
        when(tarifaRepository.findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> calculadora().depreciacion(
                datos(TipoViaje.REGULAR, UUID.randomUUID()), fechaLlegada))
                .isInstanceOf(ReglaIntegridadException.class)
                .satisfies(ex -> {
                    ReglaIntegridadException e = (ReglaIntegridadException) ex;
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.getCodigo()).isEqualTo("SIN_TARIFA");
                });
    }

    @Test
    void pagoChoferRegularUsaMultiplicador100() {
        var p = calculadora().pagoChofer(datos(TipoViaje.REGULAR, UUID.randomUUID()), fechaLlegada);

        // 150.00 × 1.00 = 150.00
        assertThat(p.getMultiplicador()).isEqualByComparingTo("1.00");
        assertThat(p.getMonto()).isEqualByComparingTo("150.00");
    }

    @Test
    void pagoChoferAlquilerPrivadoUsaMultiplicador115() {
        var p = calculadora().pagoChofer(datos(TipoViaje.ALQUILER_PRIVADO, UUID.randomUUID()), fechaLlegada);

        // 150.00 × 1.15 = 172.50
        assertThat(p.getMultiplicador()).isEqualByComparingTo("1.15");
        assertThat(p.getMonto()).isEqualByComparingTo("172.50");
        assertThat(p.getFechaPago()).isEqualTo(fechaLlegada);
    }

    @Test
    void politicaPagoChoferConcentraLosMultiplicadores() {
        assertThat(PoliticaPagoChofer.para(TipoViaje.REGULAR).multiplicador()).isEqualByComparingTo("1.00");
        assertThat(PoliticaPagoChofer.para(TipoViaje.ALQUILER_PRIVADO).multiplicador()).isEqualByComparingTo("1.15");
        assertThat(PoliticaPagoChofer.para(null).multiplicador()).isEqualByComparingTo("1.00");
    }
}
