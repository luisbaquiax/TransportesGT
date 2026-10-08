package org.transportsgt.identityservice.controllers;

/**
 * Ejemplos JSON reutilizados en la documentación OpenAPI.
 */
final class EjemplosApi {

    private EjemplosApi() {
    }

    static final String ERROR_400 = """
            {"timestamp":"2026-10-07T10:15:30","status":400,"error":"correo: debe ser una dirección de correo electrónico con formato correcto","codigo":"VALIDACION_FALLIDA","ruta":"/v1/identity/usuarios"}""";
    static final String ERROR_401 = """
            {"timestamp":"2026-10-07T10:15:30","status":401,"error":"Debe iniciar sesión: el token falta, no es válido o expiró","codigo":"NO_AUTENTICADO","ruta":"/v1/identity/usuarios"}""";
    static final String ERROR_403 = """
            {"timestamp":"2026-10-07T10:15:30","status":403,"error":"No tiene permisos para realizar esta operación","codigo":"ACCESO_DENEGADO","ruta":"/v1/identity/sucursales"}""";
    static final String ERROR_404_SUCURSAL = """
            {"timestamp":"2026-10-07T10:15:30","status":404,"error":"No se encontró la sucursal 7b1f0c1e-8f43-4b8e-9a51-2d8f8c0e9a11","codigo":"SUCURSAL_NO_ENCONTRADA","ruta":"/v1/identity/sucursales/7b1f0c1e-8f43-4b8e-9a51-2d8f8c0e9a11"}""";
    static final String ERROR_404_USUARIO = """
            {"timestamp":"2026-10-07T10:15:30","status":404,"error":"No se encontró el usuario 1c9e4a52-0d2b-4f3e-8a77-6f1b2c3d4e5f","codigo":"USUARIO_NO_ENCONTRADO","ruta":"/v1/identity/usuarios/1c9e4a52-0d2b-4f3e-8a77-6f1b2c3d4e5f"}""";
    static final String ERROR_409_SUCURSAL = """
            {"timestamp":"2026-10-07T10:15:30","status":409,"error":"Ya existe una sucursal con el nombre Quetzaltenango Centro","codigo":"NOMBRE_SUCURSAL_DUPLICADO","ruta":"/v1/identity/sucursales"}""";
    static final String ERROR_409_CORREO = """
            {"timestamp":"2026-10-07T10:15:30","status":409,"error":"Ya existe un usuario con el correo ana@transportes.gt","codigo":"CORREO_DUPLICADO","ruta":"/v1/identity/usuarios"}""";
    static final String ERROR_422_ULTIMO_ADMIN = """
            {"timestamp":"2026-10-07T10:15:30","status":422,"error":"La sucursal debe conservar al menos un administrador de sucursal activo","codigo":"ULTIMO_ADMIN_SUCURSAL","ruta":"/v1/identity/usuarios/1c9e4a52-0d2b-4f3e-8a77-6f1b2c3d4e5f/desactivar"}""";
    static final String ERROR_410_TOKEN = """
            {"timestamp":"2026-10-07T10:15:30","status":410,"error":"El enlace no es válido, ya se usó o expiró","codigo":"TOKEN_CONTRASENA_INVALIDO","ruta":"/v1/identity/auth/restablecer-contrasena"}""";

    static final String SUCURSAL = """
            {"id":"7b1f0c1e-8f43-4b8e-9a51-2d8f8c0e9a11","nombre":"Quetzaltenango Centro","departamento":"Quetzaltenango","direccion":"4a. Calle 12-35, Zona 1","telefono":"77651234","latitud":14.834500,"longitud":-91.518600,"activa":true,"version":0,"creadoEn":"2026-10-07T10:15:30-06:00","actualizadoEn":"2026-10-07T10:15:30-06:00"}""";
    static final String USUARIO = """
            {"id":"1c9e4a52-0d2b-4f3e-8a77-6f1b2c3d4e5f","correo":"cajero.xela@transportes.gt","nombreCompleto":"Ana López","rol":"CAJERO","idSucursal":"7b1f0c1e-8f43-4b8e-9a51-2d8f8c0e9a11","nombreSucursal":"Quetzaltenango Centro","activo":true,"activacionPendiente":true,"version":0,"creadoEn":"2026-10-07T10:15:30-06:00"}""";
}
