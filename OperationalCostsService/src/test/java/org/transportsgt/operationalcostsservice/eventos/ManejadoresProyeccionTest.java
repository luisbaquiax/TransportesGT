package org.transportsgt.operationalcostsservice.eventos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.payload.flota.DatosBus;
import org.transportsgt.eventos.payload.identidad.DatosSucursal;
import org.transportsgt.eventos.payload.flota.EstadoOperativo;
import org.transportsgt.operationalcostsservice.repositories.BusRefRepository;
import org.transportsgt.operationalcostsservice.repositories.SucursalRefRepository;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManejadoresProyeccionTest {

    @Mock SucursalRefRepository sucursalRefRepository;
    @Mock BusRefRepository busRefRepository;

    @SuppressWarnings("unchecked")
    private <T> SobreEvento<T> sobre(T datos, long version) {
        SobreEvento<T> evento = org.mockito.Mockito.mock(SobreEvento.class);
        when(evento.datos()).thenReturn(datos);
        when(evento.versionAgregado()).thenReturn(version);
        return evento;
    }

    @Test
    void sucursalCreadaUpsert() {
        UUID id = UUID.randomUUID();
        DatosSucursal d = new DatosSucursal(id, "Xela", "Quetzaltenango", "Centro",
                new BigDecimal("14.8"), new BigDecimal("-91.5"), true);
        new ManejadorSucursalCreada(sucursalRefRepository).manejar(sobre(d, 3L));
        verify(sucursalRefRepository).upsert(eq(id), eq("Xela"), eq(true), eq(3L));
    }

    @Test
    void sucursalActualizadaUpsert() {
        UUID id = UUID.randomUUID();
        DatosSucursal d = new DatosSucursal(id, "Capital", "Guatemala", "Centro",
                new BigDecimal("14.6"), new BigDecimal("-90.5"), false);
        new ManejadorSucursalActualizada(sucursalRefRepository).manejar(sobre(d, 5L));
        verify(sucursalRefRepository).upsert(eq(id), eq("Capital"), eq(false), eq(5L));
    }

    @Test
    void busRegistradoUpsert() {
        UUID id = UUID.randomUUID();
        UUID suc = UUID.randomUUID();
        DatosBus d = new DatosBus(id, suc, "P1ABC", "Toyota", "Hiace", 2022, 30,
                EstadoOperativo.DISPONIBLE, true);
        new ManejadorBusRegistrado(busRefRepository).manejar(sobre(d, 1L));
        verify(busRefRepository).upsert(eq(id), eq(suc), eq("P1ABC"), eq(true), eq(1L));
    }

    @Test
    void busActualizadoUpsert() {
        UUID id = UUID.randomUUID();
        UUID suc = UUID.randomUUID();
        DatosBus d = new DatosBus(id, suc, "P1ABC", "Toyota", "Hiace", 2022, 30,
                EstadoOperativo.EN_TALLER, false);
        new ManejadorBusActualizado(busRefRepository).manejar(sobre(d, 2L));
        verify(busRefRepository).upsert(eq(id), eq(suc), eq("P1ABC"), eq(false), eq(2L));
    }

    @Test
    void versionNulaUsaCero() {
        UUID id = UUID.randomUUID();
        DatosSucursal d = new DatosSucursal(id, "Y", "Z", "D",
                BigDecimal.ONE, BigDecimal.ONE, true);
        SobreEvento<DatosSucursal> evento = org.mockito.Mockito.mock(SobreEvento.class);
        when(evento.datos()).thenReturn(d);
        when(evento.versionAgregado()).thenReturn(null);
        new ManejadorSucursalCreada(sucursalRefRepository).manejar(evento);
        verify(sucursalRefRepository).upsert(eq(id), eq("Y"), eq(true), eq(0L));
    }
}
