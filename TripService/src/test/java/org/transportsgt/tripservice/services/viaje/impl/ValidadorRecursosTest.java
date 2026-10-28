package org.transportsgt.tripservice.services.viaje.impl;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.payload.flota.EstadoOperativo;
import org.transportsgt.tripservice.exception.ReglaNegocioException;
import org.transportsgt.tripservice.exception.SolicitudInvalidaException;
import org.transportsgt.tripservice.models.BusRef;
import org.transportsgt.tripservice.models.ChoferRef;
import org.transportsgt.tripservice.repositories.BusRefRepository;
import org.transportsgt.tripservice.repositories.ChoferRefRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidadorRecursosTest {

    private static final UUID SUCURSAL = UUID.randomUUID();
    private static final UUID ID_BUS = UUID.randomUUID();
    private static final UUID ID_CHOFER = UUID.randomUUID();
    /** 23:30 en Guatemala del 20 de octubre: ya es 21 en UTC. */
    private static final OffsetDateTime LLEGADA = OffsetDateTime.parse("2026-10-20T23:30:00-06:00");

    @Mock
    private BusRefRepository busRefRepository;
    @Mock
    private ChoferRefRepository choferRefRepository;

    @InjectMocks
    private ValidadorRecursos validador;

    private static BusRef.BusRefBuilder bus() {
        return BusRef.builder().id(ID_BUS).idSucursal(SUCURSAL).capacidadPasajeros((short) 40)
                .estadoOperativo(EstadoOperativo.DISPONIBLE).activo(true);
    }

    private static ChoferRef.ChoferRefBuilder chofer() {
        return ChoferRef.builder().id(ID_CHOFER).idSucursal(SUCURSAL).activo(true)
                .fechaVencimientoLicencia(LocalDate.parse("2027-01-01"));
    }

    private String codigoBus(BusRef bus, Short pasajeros) {
        when(busRefRepository.findById(ID_BUS)).thenReturn(Optional.ofNullable(bus));
        return assertThrows(ReglaNegocioException.class,
                () -> validador.validar(ID_BUS, null, SUCURSAL, LLEGADA, pasajeros)).getCodigo();
    }

    private String codigoChofer(ChoferRef chofer) {
        when(choferRefRepository.findById(ID_CHOFER)).thenReturn(Optional.ofNullable(chofer));
        return assertThrows(ReglaNegocioException.class,
                () -> validador.validar(null, ID_CHOFER, SUCURSAL, LLEGADA, null)).getCodigo();
    }

    @Test
    void validarHorario_llegadaNoPosteriorALaSalida_esInvalida() {
        assertThrows(SolicitudInvalidaException.class, () -> validador.validarHorario(LLEGADA, LLEGADA));
        assertDoesNotThrow(() -> validador.validarHorario(LLEGADA, LLEGADA.plusMinutes(1)));
    }

    @Test
    void validar_sinBusNiChofer_noConsulta() {
        validador.validar(null, null, SUCURSAL, LLEGADA, (short) 10);
        verifyNoInteractions(busRefRepository, choferRefRepository);
    }

    @Nested
    class Bus {

        @Test
        void valido_conYSinPasajeros() {
            when(busRefRepository.findById(ID_BUS)).thenReturn(Optional.of(bus().estadoOperativo(EstadoOperativo.EN_TRANSITO).build()));
            assertDoesNotThrow(() -> validador.validar(ID_BUS, null, SUCURSAL, LLEGADA, (short) 40));
            assertDoesNotThrow(() -> validador.validar(ID_BUS, null, SUCURSAL, LLEGADA, null));
        }

        @Test
        void inexistente() {
            assertEquals("BUS_NO_ENCONTRADO", codigoBus(null, null));
        }

        @Test
        void inactivo() {
            assertEquals("BUS_INACTIVO", codigoBus(bus().activo(false).build(), null));
        }

        @Test
        void deOtraSucursal() {
            assertEquals("BUS_OTRA_SUCURSAL", codigoBus(bus().idSucursal(UUID.randomUUID()).build(), null));
        }

        @Test
        void enTallerOFueraDeServicio() {
            assertEquals("BUS_NO_DISPONIBLE",
                    codigoBus(bus().estadoOperativo(EstadoOperativo.EN_TALLER).build(), null));
            assertEquals("BUS_NO_DISPONIBLE",
                    codigoBus(bus().estadoOperativo(EstadoOperativo.FUERA_DE_SERVICIO).build(), null));
        }

        @Test
        void sinCapacidadSuficiente() {
            assertEquals("CAPACIDAD_INSUFICIENTE", codigoBus(bus().build(), (short) 41));
        }
    }

    @Nested
    class Chofer {

        @Test
        void valido_licenciaVenceElDiaDeLlegadaEnHoraDeGuatemala() {
            when(choferRefRepository.findById(ID_CHOFER))
                    .thenReturn(Optional.of(chofer().fechaVencimientoLicencia(LocalDate.parse("2026-10-20")).build()));
            assertDoesNotThrow(() -> validador.validar(null, ID_CHOFER, SUCURSAL, LLEGADA, null));
        }

        @Test
        void sinFechaDeLicenciaOSinLlegada_noValidaVencimiento() {
            when(choferRefRepository.findById(ID_CHOFER))
                    .thenReturn(Optional.of(chofer().fechaVencimientoLicencia(null).build()));
            assertDoesNotThrow(() -> validador.validar(null, ID_CHOFER, SUCURSAL, LLEGADA, null));
            when(choferRefRepository.findById(ID_CHOFER)).thenReturn(Optional.of(chofer().build()));
            assertDoesNotThrow(() -> validador.validar(null, ID_CHOFER, SUCURSAL, null, null));
        }

        @Test
        void inexistente() {
            assertEquals("CHOFER_NO_ENCONTRADO", codigoChofer(null));
        }

        @Test
        void inactivo() {
            assertEquals("CHOFER_INACTIVO", codigoChofer(chofer().activo(false).build()));
        }

        @Test
        void deOtraSucursal() {
            assertEquals("CHOFER_OTRA_SUCURSAL", codigoChofer(chofer().idSucursal(UUID.randomUUID()).build()));
        }

        @Test
        void licenciaVencidaAntesDeLlegar() {
            assertEquals("LICENCIA_VENCIDA",
                    codigoChofer(chofer().fechaVencimientoLicencia(LocalDate.parse("2026-10-19")).build()));
        }
    }
}
