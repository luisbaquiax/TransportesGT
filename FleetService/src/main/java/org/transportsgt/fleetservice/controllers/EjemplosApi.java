package org.transportsgt.fleetservice.controllers;

/**
 * Ejemplos JSON reutilizados en la documentación OpenAPI.
 */
final class EjemplosApi {

    private EjemplosApi() {
    }

    static final String ERROR_400 = """
            {"timestamp":"2026-10-14T10:15:30","status":400,"error":"anioFabricacion: debe estar entre 1980 y 2100","codigo":"VALIDACION_FALLIDA","ruta":"/v1/fleet/buses"}""";
    static final String ERROR_401 = """
            {"timestamp":"2026-10-14T10:15:30","status":401,"error":"Debe iniciar sesión: el token falta, no es válido o expiró","codigo":"NO_AUTENTICADO","ruta":"/v1/fleet/buses"}""";
    static final String ERROR_403 = """
            {"timestamp":"2026-10-14T10:15:30","status":403,"error":"No tiene permisos para realizar esta operación","codigo":"ACCESO_DENEGADO","ruta":"/v1/fleet/buses"}""";
    static final String ERROR_404_BUS = """
            {"timestamp":"2026-10-14T10:15:30","status":404,"error":"No se encontró el bus 5d1c0a7e-8b43-4f2e-9a51-2d8f8c0e9a11","codigo":"BUS_NO_ENCONTRADO","ruta":"/v1/fleet/buses/5d1c0a7e-8b43-4f2e-9a51-2d8f8c0e9a11"}""";
    static final String ERROR_404_CHOFER = """
            {"timestamp":"2026-10-14T10:15:30","status":404,"error":"No se encontró el chofer 7b1f0c1e-8f43-4b8e-9a51-2d8f8c0e9a11","codigo":"CHOFER_NO_ENCONTRADO","ruta":"/v1/fleet/choferes/7b1f0c1e-8f43-4b8e-9a51-2d8f8c0e9a11"}""";
    static final String ERROR_409_PLACA = """
            {"timestamp":"2026-10-14T10:15:30","status":409,"error":"Ya existe un bus con la placa P123XYZ","codigo":"PLACA_DUPLICADA","ruta":"/v1/fleet/buses"}""";
    static final String ERROR_409_LICENCIA = """
            {"timestamp":"2026-10-14T10:15:30","status":409,"error":"Ya existe un chofer con la licencia LIC-XEL-001","codigo":"LICENCIA_DUPLICADA","ruta":"/v1/fleet/choferes"}""";
    static final String ERROR_409_BUS_CON_VIAJES = """
            {"timestamp":"2026-10-14T10:15:30","status":409,"error":"El bus 5d1c0a7e-8b43-4f2e-9a51-2d8f8c0e9a11 tiene viajes programados o en tránsito; no puede desactivarse","codigo":"BUS_CON_VIAJES","ruta":"/v1/fleet/buses/5d1c0a7e-8b43-4f2e-9a51-2d8f8c0e9a11/activo"}""";
    static final String ERROR_409_TRANSICION = """
            {"timestamp":"2026-10-14T10:15:30","status":409,"error":"EN_TRANSITO solo cambia al registrar la salida y la llegada de un viaje","codigo":"TRANSICION_INVALIDA","ruta":"/v1/fleet/buses/5d1c0a7e-8b43-4f2e-9a51-2d8f8c0e9a11/estado-operativo"}""";
    static final String ERROR_503 = """
            {"timestamp":"2026-10-14T10:15:30","status":503,"error":"El servicio de viajes no está disponible en este momento; intente de nuevo","codigo":"SERVICIO_NO_DISPONIBLE","ruta":"/v1/fleet/buses/5d1c0a7e-8b43-4f2e-9a51-2d8f8c0e9a11/activo"}""";
    static final String ERROR_400_FOTO = """
            {"timestamp":"2026-10-14T10:15:30","status":400,"error":"Formato no permitido: use JPEG, PNG o WebP","codigo":"FOTO_INVALIDA","ruta":"/v1/fleet/buses/5d1c0a7e-8b43-4f2e-9a51-2d8f8c0e9a11/foto"}""";

    static final String BUS = """
            {"id":"5d1c0a7e-8b43-4f2e-9a51-2d8f8c0e9a11","idSucursal":"10000000-0000-4000-8000-000000000001","placa":"P123XYZ","marca":"Toyota","modelo":"Hiace","anioFabricacion":2019,"capacidadPasajeros":45,"estadoOperativo":"DISPONIBLE","kilometrajeActual":12500.5,"fotoUrl":null,"activo":true,"version":0,"creadoEn":"2026-10-14T10:15:30-06:00","actualizadoEn":"2026-10-14T10:15:30-06:00"}""";
    static final String CHOFER = """
            {"id":"7b1f0c1e-8f43-4b8e-9a51-2d8f8c0e9a11","idUsuario":null,"idSucursal":"10000000-0000-4000-8000-000000000001","nombreCompleto":"Juan Carlos Pérez","correo":"chofer.xela@transportes.gt","telefono":"5555-1234","numeroLicencia":"LIC-XEL-001","tipoLicencia":"B","fechaVencimientoLicencia":"2027-03-15","salarioBaseViaje":"150.00","fotoUrl":null,"activo":true,"version":0,"creadoEn":"2026-10-14T10:15:30-06:00","actualizadoEn":"2026-10-14T10:15:30-06:00"}""";
}
