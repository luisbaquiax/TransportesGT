package org.transportsgt.privaterentalservice.jobs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.payload.Canal;
import org.transportsgt.privaterentalservice.clients.CarteraClient;
import org.transportsgt.privaterentalservice.eventos.EventosAlquiler;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;
import org.transportsgt.privaterentalservice.repositories.AlquilerRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobRecuperacionPagoAlquilerTest {

    @Mock
    AlquilerRepository alquilerRepository;
    @Mock
    CarteraClient carteraClient;
    @Mock
    EventosAlquiler eventos;

    private JobRecuperacionPagoAlquiler job() {
        return new JobRecuperacionPagoAlquiler(alquilerRepository, carteraClient, eventos);
    }

    private Alquiler alquilerCotizado(UUID id) {
        return Alquiler.builder()
                .id(id)
                .canal(Canal.EN_LINEA)
                .estado(EstadoAlquiler.COTIZADO)
                .nombreCliente("Prueba")
                .nitCliente("1234567-8")
                .idSucursalOrigen(UUID.randomUUID())
                .origen("Xela")
                .destino("Retalhuleu")
                .fechaHoraSalida(OffsetDateTime.now().plusDays(7))
                .numeroPasajeros((short) 20)
                .distanciaEstimadaKm(new java.math.BigDecimal("32.50"))
                .precioEstimado(new java.math.BigDecimal("825.00"))
                .precioFinal(new java.math.BigDecimal("1000.00"))
                .actualizadoEn(OffsetDateTime.now().minusMinutes(5))
                .version(1L)
                .build();
    }

    @Test
    void confirmaAlquilerCuandoCarteraTieneElPago() {
        UUID id = UUID.randomUUID();
        UUID idPago = UUID.randomUUID();
        Alquiler a = alquilerCotizado(id);
        when(alquilerRepository.findPagosEnDuda(eq(EstadoAlquiler.COTIZADO), eq(Canal.EN_LINEA), any()))
                .thenReturn(List.of(a));
        when(carteraClient.consultarPago(id))
                .thenReturn(Optional.of(new CarteraClient.PagoEncontrado(idPago, LocalDate.of(2026, 10, 1))));

        job().recuperarPagosEnDuda();

        ArgumentCaptor<Alquiler> captor = ArgumentCaptor.forClass(Alquiler.class);
        verify(alquilerRepository).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo(EstadoAlquiler.CONFIRMADO);
        assertThat(captor.getValue().getIdPago()).isEqualTo(idPago);
        assertThat(captor.getValue().getFechaPago()).isEqualTo(LocalDate.of(2026, 10, 1));
        verify(eventos).alquilerConfirmado(any(Alquiler.class));
    }

    @Test
    void noConfirmaSiCarteraNoTienePago() {
        UUID id = UUID.randomUUID();
        Alquiler a = alquilerCotizado(id);
        when(alquilerRepository.findPagosEnDuda(eq(EstadoAlquiler.COTIZADO), eq(Canal.EN_LINEA), any()))
                .thenReturn(List.of(a));
        when(carteraClient.consultarPago(id)).thenReturn(Optional.empty());

        job().recuperarPagosEnDuda();

        verify(alquilerRepository, never()).save(any());
        verify(eventos, never()).alquilerConfirmado(any());
        assertThat(a.getEstado()).isEqualTo(EstadoAlquiler.COTIZADO);
    }

    @Test
    void noHaceNadaSiNoHayCandidatos() {
        when(alquilerRepository.findPagosEnDuda(eq(EstadoAlquiler.COTIZADO), eq(Canal.EN_LINEA), any()))
                .thenReturn(List.of());

        job().recuperarPagosEnDuda();

        verify(carteraClient, never()).consultarPago(any());
        verify(alquilerRepository, never()).save(any());
    }

    @Test
    void ignoraErrorDeCarteraYSigueConElSiguiente() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID idPago = UUID.randomUUID();
        Alquiler a1 = alquilerCotizado(id1);
        Alquiler a2 = alquilerCotizado(id2);
        when(alquilerRepository.findPagosEnDuda(eq(EstadoAlquiler.COTIZADO), eq(Canal.EN_LINEA), any()))
                .thenReturn(List.of(a1, a2));
        when(carteraClient.consultarPago(id1))
                .thenThrow(new org.transportsgt.privaterentalservice.exception.ServicioNoDisponibleException("Cartera"));
        when(carteraClient.consultarPago(id2))
                .thenReturn(Optional.of(new CarteraClient.PagoEncontrado(idPago, LocalDate.now())));

        job().recuperarPagosEnDuda();

        verify(alquilerRepository).save(a2);
        assertThat(a2.getEstado()).isEqualTo(EstadoAlquiler.CONFIRMADO);
        assertThat(a1.getEstado()).isEqualTo(EstadoAlquiler.COTIZADO);
    }

    @Test
    void usaFechaDeHoySiCarteraNoDevuelveFecha() {
        UUID id = UUID.randomUUID();
        UUID idPago = UUID.randomUUID();
        Alquiler a = alquilerCotizado(id);
        when(alquilerRepository.findPagosEnDuda(eq(EstadoAlquiler.COTIZADO), eq(Canal.EN_LINEA), any()))
                .thenReturn(List.of(a));
        when(carteraClient.consultarPago(id))
                .thenReturn(Optional.of(new CarteraClient.PagoEncontrado(idPago, null)));

        job().recuperarPagosEnDuda();

        assertThat(a.getFechaPago()).isEqualTo(LocalDate.now());
        assertThat(a.getEstado()).isEqualTo(EstadoAlquiler.CONFIRMADO);
    }
}
