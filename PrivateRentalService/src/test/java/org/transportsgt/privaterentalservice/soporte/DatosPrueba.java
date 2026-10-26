package org.transportsgt.privaterentalservice.soporte;

import org.transportsgt.eventos.payload.Canal;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.privaterentalservice.config.security.UsuarioAutenticado;
import org.transportsgt.privaterentalservice.dto.response.AlquilerResponse;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.models.ClienteRef;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;
import org.transportsgt.privaterentalservice.models.SucursalRef;
import org.transportsgt.privaterentalservice.models.TarifaAlquiler;
import org.transportsgt.privaterentalservice.services.comun.CalendarioGuatemala;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Datos comunes de las pruebas unitarias. El reloj está fijo en 2026-10-23 06:00 de Guatemala. */
public class DatosPrueba {

    public final Clock clock = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC);
    public final CalendarioGuatemala calendario = new CalendarioGuatemala(clock);

    public final UUID idSucursal = UUID.fromString("10000000-0000-4000-8000-000000000001");
    public final UUID idCliente = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    public final OffsetDateTime salida = OffsetDateTime.parse("2026-11-10T08:00:00-06:00");

    public final UsuarioAutenticado cliente =
            new UsuarioAutenticado(idCliente, "cliente@transportes.gt", Rol.CLIENTE, null, "Cliente");
    public final UsuarioAutenticado otroCliente =
            new UsuarioAutenticado(UUID.randomUUID(), "otro@transportes.gt", Rol.CLIENTE, null, "Otro");
    public final UsuarioAutenticado adminSucursal = new UsuarioAutenticado(UUID.randomUUID(),
            "admin.xela@transportes.gt", Rol.ADMIN_SUCURSAL, idSucursal, "Admin Xela");
    public final UsuarioAutenticado adminSistema = new UsuarioAutenticado(UUID.randomUUID(),
            "admin@transportes.gt", Rol.ADMIN_SISTEMA, null, "Admin Sistema");
    public final UsuarioAutenticado cajero = new UsuarioAutenticado(UUID.randomUUID(),
            "cajero@transportes.gt", Rol.CAJERO, idSucursal, "Cajero");

    public SucursalRef sucursal(boolean activa) {
        return SucursalRef.builder().id(idSucursal).nombre("Quetzaltenango").activa(activa).version(0L).build();
    }

    public ClienteRef perfil(String nit) {
        return ClienteRef.builder().idUsuario(idCliente).nit(nit)
                .nombreCompleto("Transportes Ejemplo").activo(true).version(0L).build();
    }

    public TarifaAlquiler tarifa() {
        return TarifaAlquiler.builder()
                .precioPorKm(new BigDecimal("10.00"))
                .precioPorDia(new BigDecimal("500.00"))
                .vigenteDesde(LocalDate.of(2026, 1, 1))
                .creadoPor(UUID.randomUUID())
                .build();
    }

    public Alquiler alquiler(EstadoAlquiler estado) {
        return Alquiler.builder()
                .id(UUID.randomUUID())
                .canal(Canal.EN_LINEA)
                .estado(estado)
                .idUsuarioCliente(idCliente)
                .nombreCliente("Transportes Ejemplo")
                .nitCliente("12345678")
                .idSucursalOrigen(idSucursal)
                .origen("Quetzaltenango")
                .destino("Ciudad de Guatemala")
                .fechaHoraSalida(salida)
                .idaYVuelta(false)
                .numeroPasajeros((short) 40)
                .distanciaEstimadaKm(new BigDecimal("200.00"))
                .precioEstimado(new BigDecimal("2500.00"))
                .precioFinal(new BigDecimal("2700.00"))
                .fechaHoraLlegadaEstimada(salida.plusHours(6))
                .version(1L)
                .build();
    }

    /** Alquiler en mostrador (sin cuenta) para pagos y consultas por NIT. */
    public Alquiler alquilerMostrador(EstadoAlquiler estado, String nit) {
        Alquiler a = alquiler(estado);
        a.setCanal(Canal.MOSTRADOR);
        a.setIdUsuarioCliente(null);
        a.setNitCliente(nit);
        return a;
    }

    /** Lo que devolvería el mapper (todos los campos); para stubs. */
    public AlquilerResponse respuesta(Alquiler a) {
        return new AlquilerResponse(a.getId(), a.getCanal(), a.getEstado(), a.getIdUsuarioCliente(),
                a.getNombreCliente(), a.getNitCliente(), a.getIdSucursalOrigen(), a.getOrigen(),
                a.getDestino(), a.getFechaHoraSalida(), a.getFechaHoraRetorno(),
                a.getFechaHoraLlegadaEstimada(), a.getIdaYVuelta(), a.getNumeroPasajeros(),
                a.getLatitudOrigen(), a.getLongitudOrigen(), a.getLatitudDestino(),
                a.getLongitudDestino(), a.getDistanciaEstimadaKm(), a.getPrecioEstimado(),
                a.getPrecioFinal(), a.getIdPago(), a.getFechaPago(), a.getIdViaje(), a.getIdBus(),
                a.getPlacaBus(), a.getIdChofer(), a.getNombreChofer(), a.getMotivoCancelacion(),
                a.getCanceladoEn(), a.getVersion(), a.getCreadoEn(), List.of());
    }
}
