package org.transportsgt.reportingservice.services.exportacion.jasper;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CatalogoPlantillasJasper {
    private final Map<String, PlantillaJasper> plantillas;

    public CatalogoPlantillasJasper() {
        Map<String, PlantillaJasper> catalogo = new LinkedHashMap<>();
        agregar(catalogo, "ingresos", "reportsadmin/01-ingresos/ingresos.jrxml", "Fecha", "Tipo", "Referencia", "Ruta / trayecto", "Cliente / NIT", "Boletos", "Canal", "Monto");
        agregar(catalogo, "gastos", "reportsadmin/02-gastos/gastos.jrxml", "Fecha", "Tipo", "Referencia", "Bus", "Descripción", "Monto");
        agregar(catalogo, "ganancias", "reportsadmin/03-ganancias/ganancias.jrxml", "Sucursal", "Ingresos", "Gastos", "Ganancia neta");
        agregar(catalogo, "rutas-demandadas", "reportsadmin/04-rutas-demandadas/rutas-demandadas.jrxml", "Origen", "Destino", "Distancia", "Precio", "Boletos", "Viajes", "Ingreso", "Estado");
        agregar(catalogo, "gastos-taller-por-categoria", "reportsadmin/05-gastos-taller-categoria/gastos-taller-por-categoria.jrxml", "Bus", "Sucursal", "Fecha", "Descripción", "Tipo", "Monto");
        agregar(catalogo, "mapa-rutas", "reportsadmin/06-mapa-rutas/mapa-rutas.jrxml", "Origen", "Latitud", "Longitud", "Destino", "Latitud", "Longitud", "Distancia", "Precio");
        agregar(catalogo, "buses", "reportssucursal/07-buses/buses.jrxml", "Placa", "Marca / modelo", "Capacidad", "Estado", "Chofer", "Kilometraje", "Viajes", "Activo");
        agregar(catalogo, "choferes", "reportssucursal/08-choferes/choferes.jrxml", "Licencia", "Nombre", "Tipo", "Vencimiento", "Estado licencia", "Activo", "Viajes");
        agregar(catalogo, "ingresos-boletos", "reportssucursal/09-ingresos-boletos/ingresos-boletos.jrxml", "Ruta", "Salida", "Bus", "Boletos", "Ingreso");
        agregar(catalogo, "ingresos-alquileres", "reportssucursal/10-ingresos-alquileres/ingresos-alquileres.jrxml", "Cliente / NIT", "Origen", "Destino", "Salida", "Retorno", "Bus", "Pago", "Precio");
        agregar(catalogo, "depreciacion-buses", "reportssucursal/11-depreciacion-buses/depreciacion-buses.jrxml", "Bus", "Kilómetros", "Tarifa actual", "Depreciación", "Promedio / km");
        agregar(catalogo, "gastos-taller-por-bus", "reportssucursal/12-gastos-taller-bus/gastos-taller-por-bus.jrxml", "Bus", "Sucursal", "Fecha", "Descripción", "Tipo", "Monto");
        plantillas = Map.copyOf(catalogo);
    }

    private void agregar(Map<String, PlantillaJasper> catalogo, String nombre, String ruta, String... columnas) {
        catalogo.put(nombre, new PlantillaJasper("reportes/" + ruta, List.of(columnas)));
    }

    public PlantillaJasper obtener(String nombre) {
        PlantillaJasper plantilla = plantillas.get(nombre);
        if (plantilla == null) throw new IllegalArgumentException("Plantilla de reporte desconocida: " + nombre);
        return plantilla;
    }

    public Map<String, PlantillaJasper> todas() {
        return plantillas;
    }
}
