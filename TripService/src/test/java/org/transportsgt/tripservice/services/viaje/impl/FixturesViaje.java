package org.transportsgt.tripservice.services.viaje.impl;

import org.transportsgt.eventos.payload.flota.EstadoOperativo;
import org.transportsgt.eventos.payload.viajes.EstadoViaje;
import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.eventos.payload.viajes.TipoViaje;
import org.transportsgt.tripservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.tripservice.models.BusRef;
import org.transportsgt.tripservice.models.ChoferRef;
import org.transportsgt.tripservice.models.Ruta;
import org.transportsgt.tripservice.models.Viaje;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Datos de prueba comunes a las pruebas unitarias de viajes (los mismos que usaba ViajeServiceTest). */
public class FixturesViaje {

    public final UUID xela = UUID.fromString("10000000-0000-4000-8000-000000000001");
    public final UUID capital = UUID.fromString("10000000-0000-4000-8000-000000000002");
    public final UUID bus1 = UUID.fromString("20000000-0000-4000-8000-000000000001");
    public final UUID chofer1 = UUID.fromString("30000000-0000-4000-8000-000000000001");
    public final UUID rutaId = UUID.fromString("40000000-0000-4000-8000-000000000001");
    public final UUID usuarioChofer = UUID.fromString("e0000000-0000-4000-8000-000000000001");

    public final UsuarioAutenticado adminSucursal = new UsuarioAutenticado(
            UUID.randomUUID(), "admin@transportes.gt", Rol.ADMIN_SUCURSAL, xela, "Admin Xela");
    public final UsuarioAutenticado adminSistema = new UsuarioAutenticado(
            UUID.randomUUID(), "sis@transportes.gt", Rol.ADMIN_SISTEMA, null, "SIS");
    public final UsuarioAutenticado chofer = new UsuarioAutenticado(
            usuarioChofer, "chofer@transportes.gt", Rol.CHOFER, xela, "Juan Pérez");

    public UsuarioAutenticado adminDe(UUID sucursal) {
        return new UsuarioAutenticado(UUID.randomUUID(), "cap@transportes.gt", Rol.ADMIN_SUCURSAL, sucursal, "Admin");
    }

    public Ruta ruta() {
        return Ruta.builder().id(rutaId).idSucursalOrigen(xela).idSucursalDestino(capital)
                .distanciaKm(new BigDecimal("205.50")).precioBoleto(new BigDecimal("85.00"))
                .creadoPor(adminSucursal.id()).version(1L).build();
    }

    public BusRef busActivo() {
        return bus(bus1, xela, true);
    }

    public BusRef bus(UUID id, UUID sucursal, boolean activo) {
        return BusRef.builder().id(id).idSucursal(sucursal).placa("P123ABC")
                .capacidadPasajeros((short) 45).estadoOperativo(EstadoOperativo.DISPONIBLE)
                .activo(activo).version(1L).build();
    }

    public ChoferRef choferRef() {
        return ChoferRef.builder().id(chofer1).idUsuario(usuarioChofer).idSucursal(xela)
                .nombreCompleto("Juan Pérez").salarioBaseViaje(new BigDecimal("150.00"))
                .fechaVencimientoLicencia(LocalDate.now().plusYears(2)).activo(true).version(1L).build();
    }

    public Viaje viajeProgramado() {
        Viaje v = Viaje.builder()
                .id(UUID.randomUUID())
                .tipoViaje(TipoViaje.REGULAR)
                .estado(EstadoViaje.PROGRAMADO)
                .ruta(ruta())
                .idSucursalOrigen(xela)
                .nombreOrigen("Quetzaltenango")
                .nombreDestino("Ciudad de Guatemala")
                .idBus(bus1)
                .idChofer(chofer1)
                .fechaHoraSalida(OffsetDateTime.now().plusDays(7))
                .fechaHoraLlegadaEstimada(OffsetDateTime.now().plusDays(7).plusHours(4))
                .precioBoleto(new BigDecimal("85.00"))
                .creadoPor(adminSucursal.id())
                .version(1L)
                .build();
        return v;
    }
}
