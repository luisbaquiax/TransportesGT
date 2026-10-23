package org.transportsgt.tripservice.services.viaje.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.payload.EstadoViaje;
import org.transportsgt.tripservice.dto.request.Requests;
import org.transportsgt.tripservice.eventos.EventosViaje;
import org.transportsgt.tripservice.eventos.RecursosEvento;
import org.transportsgt.tripservice.exception.ReglaNegocioException;
import org.transportsgt.tripservice.exception.SolicitudInvalidaException;
import org.transportsgt.tripservice.mappers.EnsambladorViajes;
import org.transportsgt.tripservice.mappers.ViajeMapperImpl;
import org.transportsgt.tripservice.models.RegistroLlegada;
import org.transportsgt.tripservice.models.RegistroSalida;
import org.transportsgt.tripservice.models.Viaje;
import org.transportsgt.tripservice.repositories.BusRefRepository;
import org.transportsgt.tripservice.repositories.ChoferRefRepository;
import org.transportsgt.tripservice.repositories.RegistroLlegadaRepository;
import org.transportsgt.tripservice.repositories.RegistroSalidaRepository;
import org.transportsgt.tripservice.repositories.ViajeRepository;
import org.transportsgt.tripservice.services.autorizacion.AutorizacionViaje;
import org.transportsgt.tripservice.services.comun.BuscadorViajes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistroViajeServiceImplTest {

    @Mock
    private ViajeRepository viajeRepository;
    @Mock
    private BusRefRepository busRefRepository;
    @Mock
    private ChoferRefRepository choferRefRepository;
    @Mock
    private RegistroSalidaRepository registroSalidaRepository;
    @Mock
    private RegistroLlegadaRepository registroLlegadaRepository;
    @Mock
    private EventosViaje eventos;

    private final FixturesViaje f = new FixturesViaje();
    private RegistroViajeServiceImpl servicio;

    @BeforeEach
    void setUp() {
        servicio = new RegistroViajeServiceImpl(new BuscadorViajes(viajeRepository),
                new AutorizacionViaje(choferRefRepository), registroSalidaRepository, registroLlegadaRepository,
                choferRefRepository, busRefRepository, new GuardadoViaje(viajeRepository),
                new RecursosEvento(busRefRepository, choferRefRepository), eventos,
                new EnsambladorViajes(busRefRepository, choferRefRepository, registroSalidaRepository,
                        registroLlegadaRepository, new ViajeMapperImpl()));
    }

    private RegistroSalida salidaDe(Viaje v) {
        return RegistroSalida.builder()
                .viaje(v).idBus(f.bus1).idChofer(f.chofer1)
                .fechaHoraSalidaReal(OffsetDateTime.now().minusHours(4))
                .kilometrajeInicial(new BigDecimal("123450.00"))
                .registradoPor(f.adminSucursal.id()).build();
    }

    @Test
    void registrarSalidaPasaEnTransitoYPublica() {
        Viaje v = f.viajeProgramado();
        Requests.SalidaRequest req = new Requests.SalidaRequest(OffsetDateTime.now(), new BigDecimal("123450.00"));
        Viaje enTransito = f.viajeProgramado();
        enTransito.setEstado(EstadoViaje.EN_TRANSITO);
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));
        when(registroSalidaRepository.existsByViajeId(v.getId())).thenReturn(false);
        when(registroSalidaRepository.save(any())).thenReturn(null);
        when(viajeRepository.saveAndFlush(any(Viaje.class))).thenReturn(enTransito);

        var resp = servicio.registrarSalida(v.getId(), req, f.adminSucursal);

        assertEquals(EstadoViaje.EN_TRANSITO, resp.estado());
        verify(eventos).salidaRegistrada(any(), any(), any(), any());
    }

    @Test
    void registrarSalidaRechazaDuplicada() {
        Viaje v = f.viajeProgramado();
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));
        when(registroSalidaRepository.existsByViajeId(v.getId())).thenReturn(true);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.registrarSalida(v.getId(),
                        new Requests.SalidaRequest(OffsetDateTime.now(), BigDecimal.ONE), f.adminSucursal));
        assertEquals("SALIDA_YA_REGISTRADA", ex.getCodigo());
    }

    @Test
    void registrarLlegadaCalculaDistanciaYFinaliza() {
        Viaje v = f.viajeProgramado();
        v.setEstado(EstadoViaje.EN_TRANSITO);
        Requests.LlegadaRequest req = new Requests.LlegadaRequest(
                OffsetDateTime.now(), new BigDecimal("123655.50"), new BigDecimal("420.00"));
        Viaje finalizado = f.viajeProgramado();
        finalizado.setId(v.getId());
        finalizado.setEstado(EstadoViaje.FINALIZADO);
        RegistroLlegada llegadaGuardada = RegistroLlegada.builder()
                .viaje(finalizado)
                .fechaHoraLlegadaReal(req.fechaHoraLlegadaReal())
                .kilometrajeFinal(req.kilometrajeFinal())
                .distanciaRecorridaKm(new BigDecimal("205.50"))
                .gastoCombustibleTotal(req.gastoCombustibleTotal())
                .salarioBaseChofer(new BigDecimal("150.00"))
                .registradoPor(f.adminSucursal.id())
                .build();
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));
        when(registroLlegadaRepository.existsByViajeId(v.getId())).thenReturn(false);
        when(registroSalidaRepository.findByViajeId(v.getId())).thenReturn(Optional.of(salidaDe(v)));
        when(choferRefRepository.findById(f.chofer1)).thenReturn(Optional.of(f.choferRef()));
        when(busRefRepository.findById(f.bus1)).thenReturn(Optional.of(f.busActivo()));
        when(registroLlegadaRepository.save(any())).thenReturn(null);
        when(viajeRepository.saveAndFlush(any(Viaje.class))).thenReturn(finalizado);
        // La respuesta se arma en lote: un solo findAllByViajeIdIn para los registros de llegada
        when(registroLlegadaRepository.findAllByViajeIdIn(any())).thenReturn(List.of(llegadaGuardada));

        var resp = servicio.registrarLlegada(v.getId(), req, f.adminSucursal);

        assertEquals(EstadoViaje.FINALIZADO, resp.estado());
        assertNotNull(resp.registroLlegada());
        assertEquals(new BigDecimal("205.50"), resp.registroLlegada().distanciaRecorridaKm());
        verify(eventos).llegadaRegistrada(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void registrarLlegadaRechazaKilometrajeMenor() {
        Viaje v = f.viajeProgramado();
        v.setEstado(EstadoViaje.EN_TRANSITO);
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));
        when(registroLlegadaRepository.existsByViajeId(v.getId())).thenReturn(false);
        when(registroSalidaRepository.findByViajeId(v.getId())).thenReturn(Optional.of(salidaDe(v)));

        assertThrows(SolicitudInvalidaException.class, () -> servicio.registrarLlegada(v.getId(),
                new Requests.LlegadaRequest(OffsetDateTime.now(), new BigDecimal("100000.00"),
                        BigDecimal.TEN), f.adminSucursal));
    }
}
