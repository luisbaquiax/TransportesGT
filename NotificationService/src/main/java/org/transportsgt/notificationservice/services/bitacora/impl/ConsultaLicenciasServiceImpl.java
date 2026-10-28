package org.transportsgt.notificationservice.services.bitacora.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.notificationservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.notificationservice.dto.response.PaginaResponse;
import org.transportsgt.notificationservice.dto.response.RecordatorioLicenciaResponse;
import org.transportsgt.notificationservice.exception.SinPermisoSucursalException;
import org.transportsgt.notificationservice.mappers.RecordatorioMapper;
import org.transportsgt.notificationservice.models.EstadoRecordatorio;
import org.transportsgt.notificationservice.repositories.RecordatorioLicenciaRepository;
import org.transportsgt.notificationservice.repositories.RecordatorioSpecs;
import org.transportsgt.notificationservice.services.bitacora.ConsultaLicenciasService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConsultaLicenciasServiceImpl implements ConsultaLicenciasService {

    private final RecordatorioLicenciaRepository recordatorioRepository;
    private final RecordatorioSpecs specs;
    private final RecordatorioMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<RecordatorioLicenciaResponse> listar(UsuarioAutenticado usuario, UUID idSucursal,
                                                               EstadoRecordatorio estado, int pagina, int tamano) {
        UUID sucursal = idSucursal;
        if (usuario.esAdminSucursal()) {
            // Sin idSucursal en el token no hay sucursal propia: no se le muestran las de todas
            if (usuario.idSucursal() == null || idSucursal != null && !usuario.perteneceASucursal(idSucursal)) {
                throw new SinPermisoSucursalException();
            }
            sucursal = usuario.idSucursal();
        }
        return PaginaResponse.de(recordatorioRepository.findAll(specs.filtrar(sucursal, estado),
                PageRequest.of(pagina, tamano, Sort.by("fechaEnvioProgramada", "id"))), mapper::toResponse);
    }
}
