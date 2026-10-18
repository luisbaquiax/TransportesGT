package org.transportsgt.privaterentalservice.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.transportsgt.privaterentalservice.config.security.UsuarioAutenticado;
import org.transportsgt.privaterentalservice.dto.request.CotizacionRequest;
import org.transportsgt.privaterentalservice.dto.request.PagoAlquilerRequest;
import org.transportsgt.privaterentalservice.dto.request.SolicitudAlquilerRequest;
import org.transportsgt.privaterentalservice.dto.request.SolicitudMostradorRequest;
import org.transportsgt.privaterentalservice.dto.response.AlquilerResponse;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;

import java.time.LocalDate;
import java.util.UUID;

public interface AlquilerService {

    AlquilerResponse solicitarEnLinea(SolicitudAlquilerRequest request, UsuarioAutenticado usuario);

    AlquilerResponse solicitarMostrador(SolicitudMostradorRequest request, UsuarioAutenticado usuario);

    AlquilerResponse cotizar(UUID id, CotizacionRequest request, UsuarioAutenticado usuario);

    AlquilerResponse pagarEnLinea(UUID id, PagoAlquilerRequest request, UsuarioAutenticado usuario,
                                  String authorizationHeader);

    AlquilerResponse pagarMostrador(UUID id, PagoAlquilerRequest request, UsuarioAutenticado usuario);

    AlquilerResponse detalle(UUID id, UsuarioAutenticado usuario);

    Page<AlquilerResponse> listar(EstadoAlquiler estado, LocalDate desde, LocalDate hasta,
                                  Boolean sinAsignar, UsuarioAutenticado usuario, Pageable pageable);

    Page<AlquilerResponse> mios(UsuarioAutenticado usuario, Pageable pageable);
}
