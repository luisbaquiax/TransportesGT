package org.transportsgt.tripservice.services.proyeccion.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.payload.alquileres.DatosAlquilerConfirmado;
import org.transportsgt.eventos.payload.viajes.EstadoViaje;
import org.transportsgt.eventos.payload.viajes.TipoViaje;
import org.transportsgt.tripservice.eventos.EventosViaje;
import org.transportsgt.tripservice.models.Viaje;
import org.transportsgt.tripservice.repositories.ViajeRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViajesAlquilerServiceImplTest {

    private static final OffsetDateTime SALIDA = OffsetDateTime.parse("2026-11-02T07:00:00-06:00");

    @Mock
    private ViajeRepository viajeRepository;
    @Mock
    private EventosViaje eventos;

    @InjectMocks
    private ViajesAlquilerServiceImpl servicio;

    private static DatosAlquilerConfirmado alquiler(UUID idUsuarioCliente) {
        return new DatosAlquilerConfirmado(UUID.randomUUID(), idUsuarioCliente, "Cliente", "CF", UUID.randomUUID(),
                "Xela", "Antigua", SALIDA, null, SALIDA.plusHours(3), false, 30,
                new BigDecimal("2500.00"), UUID.randomUUID(), LocalDate.parse("2026-10-20"));
    }

    @Test
    void crearDesdeAlquiler_creaViajePendienteDeAsignacionYPublicaElEvento() {
        // Arrange
        UUID idCliente = UUID.randomUUID();
        DatosAlquilerConfirmado datos = alquiler(idCliente);
        when(viajeRepository.existsByIdAlquiler(datos.idAlquiler())).thenReturn(false);
        when(viajeRepository.save(any(Viaje.class))).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<Viaje> captor = ArgumentCaptor.forClass(Viaje.class);

        // Act
        servicio.crearDesdeAlquiler(datos);

        // Assert
        verify(viajeRepository).save(captor.capture());
        Viaje viaje = captor.getValue();
        assertEquals(TipoViaje.ALQUILER_PRIVADO, viaje.getTipoViaje());
        assertEquals(EstadoViaje.PENDIENTE_ASIGNACION, viaje.getEstado());
        assertEquals(datos.idAlquiler(), viaje.getIdAlquiler());
        assertEquals((short) 30, viaje.getNumeroPasajeros());
        assertEquals(idCliente, viaje.getCreadoPor());
        verify(eventos).viajeProgramado(viaje, null, null);
    }

    @Test
    void crearDesdeAlquiler_sinCliente_generaUnCreador() {
        // Arrange
        DatosAlquilerConfirmado datos = alquiler(null);
        when(viajeRepository.existsByIdAlquiler(datos.idAlquiler())).thenReturn(false);
        when(viajeRepository.save(any(Viaje.class))).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<Viaje> captor = ArgumentCaptor.forClass(Viaje.class);

        // Act
        servicio.crearDesdeAlquiler(datos);

        // Assert
        verify(viajeRepository).save(captor.capture());
        assertNotNull(captor.getValue().getCreadoPor());
    }

    @Test
    void crearDesdeAlquiler_yaExistente_esIdempotente() {
        // Arrange
        DatosAlquilerConfirmado datos = alquiler(UUID.randomUUID());
        when(viajeRepository.existsByIdAlquiler(datos.idAlquiler())).thenReturn(true);

        // Act
        servicio.crearDesdeAlquiler(datos);

        // Assert
        verify(viajeRepository, never()).save(any());
        verifyNoInteractions(eventos);
    }
}
