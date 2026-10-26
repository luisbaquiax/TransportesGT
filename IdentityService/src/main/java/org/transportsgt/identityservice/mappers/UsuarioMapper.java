package org.transportsgt.identityservice.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.transportsgt.eventos.payload.DatosUsuario;
import org.transportsgt.identityservice.dto.response.UsuarioResponse;
import org.transportsgt.identityservice.models.Usuario;

@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    @Mapping(target = "idSucursal", source = "sucursal.id")
    @Mapping(target = "nombreSucursal", source = "sucursal.nombre")
    @Mapping(target = "activacionPendiente", expression = "java(usuario.getContrasenaCambiadaEn() == null)")
    UsuarioResponse toResponse(Usuario usuario);

    /**
     * Payload de UsuarioCreado / UsuarioActualizado.
     */
    @Mapping(target = "idUsuario", source = "id")
    @Mapping(target = "idSucursal", source = "sucursal.id")
    DatosUsuario toDatos(Usuario usuario);
}
