package org.transportsgt.operationalcostsservice.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.transportsgt.eventos.payload.DatosDepreciacion;
import org.transportsgt.eventos.payload.DatosGastoCombustible;
import org.transportsgt.eventos.payload.DatosGastoTaller;
import org.transportsgt.eventos.payload.DatosPagoChofer;
import org.transportsgt.eventos.payload.DatosTarifaDepreciacion;
import org.transportsgt.eventos.payload.LineaTaller;
import org.transportsgt.operationalcostsservice.dto.response.CategoriaResponse;
import org.transportsgt.operationalcostsservice.dto.response.GastoTallerResponse;
import org.transportsgt.operationalcostsservice.dto.response.ResumenCostosViajeResponse;
import org.transportsgt.operationalcostsservice.dto.response.TarifaDepreciacionResponse;
import org.transportsgt.operationalcostsservice.models.CategoriaGasto;
import org.transportsgt.operationalcostsservice.models.ConfiguracionDepreciacion;
import org.transportsgt.operationalcostsservice.models.GastoCombustible;
import org.transportsgt.operationalcostsservice.models.GastoTaller;
import org.transportsgt.operationalcostsservice.models.GastoTallerLinea;
import org.transportsgt.operationalcostsservice.models.PagoChofer;
import org.transportsgt.operationalcostsservice.models.RegistroDepreciacion;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface CostosMapper {

    TarifaDepreciacionResponse toResponse(ConfiguracionDepreciacion c);

    List<TarifaDepreciacionResponse> toTarifaList(List<ConfiguracionDepreciacion> lista);

    @Mapping(target = "idCategoria", source = "categoria.id")
    @Mapping(target = "nombreCategoria", source = "categoria.nombre")
    GastoTallerResponse.LineaTallerResponse toLineaResponse(GastoTallerLinea l);

    List<GastoTallerResponse.LineaTallerResponse> toLineaResponseList(List<GastoTallerLinea> lineas);

    GastoTallerResponse toResponse(GastoTaller g);

    CategoriaResponse toResponse(CategoriaGasto c);

    List<CategoriaResponse> toCategoriaList(List<CategoriaGasto> lista);

    @Mapping(target = "idGasto", source = "g.id")
    DatosGastoCombustible toDatosCombustible(GastoCombustible g);

    @Mapping(target = "idDepreciacion", source = "r.id")
    @Mapping(target = "montoPorKm", source = "r.montoPorKm")
    @Mapping(target = "fechaCalculo", source = "r.fechaCalculo")
    DatosDepreciacion toDatosDepreciacion(RegistroDepreciacion r);

    @Mapping(target = "idPago", source = "p.id")
    @Mapping(target = "fechaPago", source = "p.fechaPago")
    DatosPagoChofer toDatosPagoChofer(PagoChofer p);

    @Mapping(target = "idGasto", source = "g.id")
    @Mapping(target = "fechaServicio", source = "g.fechaServicio")
    @Mapping(target = "lineas", source = "lineas")
    DatosGastoTaller toDatosGastoTaller(GastoTaller g, List<LineaTaller> lineas);

    @Mapping(target = "idLinea", source = "l.id")
    @Mapping(target = "idCategoria", source = "l.categoria.id")
    @Mapping(target = "nombreCategoria", source = "l.categoria.nombre")
    LineaTaller toLineaTaller(GastoTallerLinea l);

    List<LineaTaller> toLineaTallerList(List<GastoTallerLinea> lineas);

    @Mapping(target = "idGasto", source = "g.id")
    @Mapping(target = "idBus", source = "g.idBus")
    @Mapping(target = "idSucursal", source = "g.idSucursal")
    @Mapping(target = "fechaGasto", source = "g.fechaGasto")
    ResumenCostosViajeResponse.CombustibleResponse toCombustibleResponse(GastoCombustible g);

    @Mapping(target = "idDepreciacion", source = "r.id")
    @Mapping(target = "fechaCalculo", source = "r.fechaCalculo")
    ResumenCostosViajeResponse.DepreciacionResponse toDepreciacionResponse(RegistroDepreciacion r);

    @Mapping(target = "idPago", source = "p.id")
    @Mapping(target = "fechaPago", source = "p.fechaPago")
    ResumenCostosViajeResponse.PagoChoferResponse toPagoChoferResponse(PagoChofer p);

    default DatosTarifaDepreciacion toDatosTarifa(BigDecimal montoPorKm, java.time.LocalDate vigenteDesde,
                                                   UUID idUsuarioCambio) {
        return new DatosTarifaDepreciacion(montoPorKm, vigenteDesde, idUsuarioCambio);
    }
}
