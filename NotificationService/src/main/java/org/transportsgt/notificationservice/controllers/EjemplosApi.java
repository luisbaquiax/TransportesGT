package org.transportsgt.notificationservice.controllers;

final class EjemplosApi {

    private EjemplosApi() {
    }

    static final String ERROR_400 = """
            {"timestamp":"2026-10-20T10:15:30","status":400,"error":"La fecha 'desde' no puede ser posterior a 'hasta'","codigo":"SOLICITUD_INVALIDA","ruta":"/v1/notifications/notificaciones"}""";
    static final String ERROR_401 = """
            {"timestamp":"2026-10-20T10:15:30","status":401,"error":"Debe iniciar sesión: el token falta, no es válido o expiró","codigo":"NO_AUTENTICADO","ruta":"/v1/notifications/notificaciones"}""";
    static final String ERROR_403 = """
            {"timestamp":"2026-10-20T10:15:30","status":403,"error":"No tiene permisos para realizar esta operación","codigo":"ACCESO_DENEGADO","ruta":"/v1/notifications/notificaciones"}""";
    static final String ERROR_403_SUCURSAL = """
            {"timestamp":"2026-10-20T10:15:30","status":403,"error":"Solo puede consultar los avisos de su sucursal","codigo":"SIN_PERMISO","ruta":"/v1/notifications/notificaciones/licencias"}""";
    static final String ERROR_404 = """
            {"timestamp":"2026-10-20T10:15:30","status":404,"error":"La notificación no existe","codigo":"NO_ENCONTRADO","ruta":"/v1/notifications/notificaciones/3f2b1c0d-9e8f-4a7b-8c6d-5e4f3a2b1c0d"}""";
    static final String ERROR_409_ESTADO = """
            {"timestamp":"2026-10-20T10:15:30","status":409,"error":"Solo se reintenta un correo FALLIDA (estado actual: ENVIADA)","codigo":"REINTENTO_NO_PERMITIDO","ruta":"/v1/notifications/notificaciones/3f2b1c0d-9e8f-4a7b-8c6d-5e4f3a2b1c0d/reintentar"}""";
    static final String ERROR_409_ENLACE = """
            {"timestamp":"2026-10-20T10:15:30","status":409,"error":"Un enlace de contraseña no se reenvía: el usuario debe solicitar uno nuevo","codigo":"REINTENTO_NO_PERMITIDO","ruta":"/v1/notifications/notificaciones/3f2b1c0d-9e8f-4a7b-8c6d-5e4f3a2b1c0d/reintentar"}""";

    static final String BITACORA = """
            {"contenido":[{"idNotificacion":"3f2b1c0d-9e8f-4a7b-8c6d-5e4f3a2b1c0d","tipo":"COMPRA_BOLETOS","correoDestino":"carlos.mendez@gmail.com","idUsuario":"0b5a7c1e-2d3f-4a5b-8c9d-0e1f2a3b4c5d","asunto":"Confirmación de compra de boletos","estado":"FALLIDA","intentos":5,"ultimoError":"ConnectException: Connection refused","proximoIntentoEn":"2026-10-20T16:45:00Z","creadoEn":"2026-10-20T16:30:00Z","enviadaEn":null}],"pagina":0,"tamano":20,"totalElementos":1,"totalPaginas":1}""";
    static final String RESUMEN = """
            {"idNotificacion":"3f2b1c0d-9e8f-4a7b-8c6d-5e4f3a2b1c0d","tipo":"COMPRA_BOLETOS","correoDestino":"carlos.mendez@gmail.com","idUsuario":"0b5a7c1e-2d3f-4a5b-8c9d-0e1f2a3b4c5d","asunto":"Confirmación de compra de boletos","estado":"PENDIENTE","intentos":0,"ultimoError":"ConnectException: Connection refused","proximoIntentoEn":"2026-10-20T17:00:00Z","creadoEn":"2026-10-20T16:30:00Z","enviadaEn":null}""";
    static final String DETALLE = """
            {"idNotificacion":"3f2b1c0d-9e8f-4a7b-8c6d-5e4f3a2b1c0d","tipo":"COMPRA_BOLETOS","correoDestino":"carlos.mendez@gmail.com","idUsuario":"0b5a7c1e-2d3f-4a5b-8c9d-0e1f2a3b4c5d","asunto":"Confirmación de compra de boletos","cuerpo":"<!DOCTYPE html><html lang=\\"es\\">…</html>","estado":"ENVIADA","intentos":1,"ultimoError":null,"proximoIntentoEn":"2026-10-20T16:30:00Z","claveDedup":"evt:7c1d2e3f-1111-4a2b-9c3d-000000000001:carlos.mendez@gmail.com","idEventoOrigen":"7c1d2e3f-1111-4a2b-9c3d-000000000001","creadoEn":"2026-10-20T16:30:00Z","enviadaEn":"2026-10-20T16:30:21Z"}""";
    static final String LICENCIAS = """
            {"contenido":[{"idRecordatorio":"5e4d3c2b-1a09-4f8e-9d7c-6b5a4f3e2d1c","idChofer":"2f3e4d5c-6b7a-4891-8a0b-1c2d3e4f5a6b","nombreChofer":"Juan Pérez","idSucursal":"10000000-0000-4000-8000-000000000001","fechaVencimiento":"2027-03-15","diasAnticipacion":30,"fechaEnvioProgramada":"2027-02-13","estado":"PENDIENTE","enviadoEn":null}],"pagina":0,"tamano":20,"totalElementos":1,"totalPaginas":1}""";
    static final String PROCESAMIENTO = """
            {"licencias":{"enviados":2,"cancelados":1,"conError":0},"envio":{"enviadas":7,"reprogramadas":1,"fallidas":0}}""";
}
