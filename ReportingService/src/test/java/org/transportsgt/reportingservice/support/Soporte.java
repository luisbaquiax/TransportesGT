package org.transportsgt.reportingservice.support;

import org.springframework.security.oauth2.jwt.Jwt;
import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.reportingservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.reportingservice.services.comun.ArmadorReporteTabla;
import org.transportsgt.reportingservice.services.comun.FilasTaller;
import org.transportsgt.reportingservice.services.comun.TextosReporte;
import org.transportsgt.reportingservice.utils.Dinero;
import org.transportsgt.reportingservice.utils.Fechas;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Componentes reales (sin estado) y usuarios del token para las pruebas unitarias.
 */
public final class Soporte {

    /** 18:00 UTC del 24 = 12:00 del 24 en Guatemala. */
    public static final Instant AHORA = Instant.parse("2026-10-24T18:00:00Z");
    public static final LocalDate HOY = LocalDate.parse("2026-10-24");

    private Soporte() {
    }

    public static Fechas fechas() {
        return new Fechas(Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    public static Dinero dinero() {
        return new Dinero();
    }

    public static ArmadorReporteTabla armador() {
        return new ArmadorReporteTabla(dinero(), fechas());
    }

    public static TextosReporte textos() {
        return new TextosReporte();
    }

    public static FilasTaller filasTaller() {
        return new FilasTaller(armador(), textos());
    }

    public static UsuarioAutenticado adminSistema() {
        return new UsuarioAutenticado(UUID.randomUUID(), "sis@transportes.gt", Rol.ADMIN_SISTEMA, null, "Sistema");
    }

    public static UsuarioAutenticado adminSucursal(UUID idSucursal) {
        return new UsuarioAutenticado(UUID.randomUUID(), "suc@transportes.gt", Rol.ADMIN_SUCURSAL, idSucursal,
                "Sucursal");
    }

    public static Jwt jwt(Rol rol, UUID idSucursal) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "HS256")
                .subject(UUID.randomUUID().toString()).claim("rol", rol.name()).claim("correo", "u@transportes.gt");
        if (idSucursal != null) {
            builder.claim("idSucursal", idSucursal.toString());
        }
        return builder.build();
    }
}
