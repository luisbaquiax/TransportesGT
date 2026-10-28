package org.transportsgt.tripservice.eventos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.payload.flota.DatosBus;
import org.transportsgt.eventos.payload.flota.DatosChofer;
import org.transportsgt.eventos.payload.identidad.DatosSucursal;
import org.transportsgt.eventos.payload.flota.EstadoOperativo;
import org.transportsgt.eventos.payload.flota.TipoLicencia;
import org.transportsgt.tripservice.repositories.BusRefRepository;
import org.transportsgt.tripservice.repositories.ChoferRefRepository;
import org.transportsgt.tripservice.repositories.SucursalRefRepository;
import org.transportsgt.tripservice.services.proyeccion.impl.ProyeccionReferenciasServiceImpl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ManejadoresProyeccionTest {

    @Mock
    private SucursalRefRepository sucursalRepository;
    @Mock
    private BusRefRepository busRepository;
    @Mock
    private ChoferRefRepository choferRepository;

    /** Los manejadores delegan en la proyección; se prueba el recorrido completo hasta el upsert. */
    private ProyeccionReferenciasServiceImpl proyeccion() {
        return new ProyeccionReferenciasServiceImpl(sucursalRepository, busRepository, choferRepository);
    }

    private <T> SobreEvento<T> sobre(Long version, T datos) {
        return new SobreEvento<>(UUID.randomUUID(), "prueba", 1, Instant.now(),
                "pruebas", UUID.randomUUID(), version, null, datos);
    }

    @Test
    void sucursalCreadaHaceUpsert() {
        UUID id = UUID.randomUUID();
        DatosSucursal d = new DatosSucursal(id, "Quetzaltenango", "Quetzaltenango",
                "Calle 1", null, null, true);
        new ManejadorSucursalCreada(proyeccion()).manejar(sobre(3L, d));
        verify(sucursalRepository).upsert(id, "Quetzaltenango", true, 3L);
    }

    @Test
    void sucursalCreadaConVersionNulaUsaCero() {
        UUID id = UUID.randomUUID();
        DatosSucursal d = new DatosSucursal(id, "X", null, null, null, null, false);
        new ManejadorSucursalCreada(proyeccion()).manejar(sobre(null, d));
        verify(sucursalRepository).upsert(id, "X", false, 0L);
    }

    @Test
    void sucursalActualizadaHaceUpsert() {
        UUID id = UUID.randomUUID();
        DatosSucursal d = new DatosSucursal(id, "Huehue", null, null, null, null, true);
        new ManejadorSucursalActualizada(proyeccion()).manejar(sobre(5L, d));
        verify(sucursalRepository).upsert(id, "Huehue", true, 5L);
    }

    @Test
    void busRegistradoHaceUpsert() {
        UUID id = UUID.randomUUID();
        UUID suc = UUID.randomUUID();
        DatosBus d = new DatosBus(id, suc, "P123ABC", "Mercedes", "O500", 2022, 45,
                EstadoOperativo.DISPONIBLE, true);
        new ManejadorBusRegistrado(proyeccion()).manejar(sobre(2L, d));
        verify(busRepository).upsert(id, suc, "P123ABC", (short) 45, "DISPONIBLE", true, 2L);
    }

    @Test
    void busActualizadoHaceUpsertConVersionNula() {
        UUID id = UUID.randomUUID();
        UUID suc = UUID.randomUUID();
        DatosBus d = new DatosBus(id, suc, "P999ZZZ", "Hino", "FC", 2020, 30,
                EstadoOperativo.EN_TALLER, false);
        new ManejadorBusActualizado(proyeccion()).manejar(sobre(null, d));
        verify(busRepository).upsert(id, suc, "P999ZZZ", (short) 30, "EN_TALLER", false, 0L);
    }

    @Test
    void choferRegistradoHaceUpsert() {
        UUID id = UUID.randomUUID();
        UUID suc = UUID.randomUUID();
        UUID usuario = UUID.randomUUID();
        DatosChofer d = new DatosChofer(id, usuario, suc, "Juan Pérez",
                "juan@t.gt", "5555", "LIC-1", TipoLicencia.E,
                LocalDate.of(2027, 12, 31), new BigDecimal("150.00"), true);
        new ManejadorChoferRegistrado(proyeccion()).manejar(sobre(1L, d));
        verify(choferRepository).upsert(eq(id), eq(usuario), eq(suc), eq("Juan Pérez"),
                eq(new BigDecimal("150.00")), eq(LocalDate.of(2027, 12, 31)), eq(true), eq(1L));
    }

    @Test
    void choferActualizadoHaceUpsert() {
        UUID id = UUID.randomUUID();
        UUID suc = UUID.randomUUID();
        DatosChofer d = new DatosChofer(id, null, suc, "María López",
                "maria@t.gt", "5556", "LIC-2", TipoLicencia.C,
                LocalDate.of(2028, 6, 30), new BigDecimal("140.00"), false);
        new ManejadorChoferActualizado(proyeccion()).manejar(sobre(4L, d));
        verify(choferRepository).upsert(eq(id), isNull(), eq(suc), eq("María López"),
                eq(new BigDecimal("140.00")), eq(LocalDate.of(2028, 6, 30)), eq(false), eq(4L));
    }
}
