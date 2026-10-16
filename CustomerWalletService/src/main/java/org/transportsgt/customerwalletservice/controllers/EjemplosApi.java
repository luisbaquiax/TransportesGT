package org.transportsgt.customerwalletservice.controllers;

/**
 * Ejemplos de respuesta para la documentación OpenAPI (springdoc).
 */
final class EjemplosApi {

    private EjemplosApi() {
    }

    static final String ERROR_400 = """
            {"timestamp":"2026-10-20T10:15:30","status":400,"error":"monto: debe ser mayor que o igual a 0.01","codigo":"VALIDACION_FALLIDA","ruta":"/v1/customers/cartera/recargas"}""";
    static final String ERROR_400_NIT = """
            {"timestamp":"2026-10-20T10:15:30","status":400,"error":"El NIT debe tener de 2 a 20 letras o dígitos y no puede ser CF","codigo":"VALIDACION_FALLIDA","ruta":"/v1/customers/clientes/yo"}""";
    static final String ERROR_400_CLAVE = """
            {"timestamp":"2026-10-20T10:15:30","status":400,"error":"Falta la cabecera Clave-Idempotencia","codigo":"SOLICITUD_INVALIDA","ruta":"/v1/customers/cartera/recargas"}""";
    static final String ERROR_401 = """
            {"timestamp":"2026-10-20T10:15:30","status":401,"error":"Debe iniciar sesión: el token falta, no es válido o expiró","codigo":"NO_AUTENTICADO","ruta":"/v1/customers/cartera/yo"}""";
    static final String ERROR_403 = """
            {"timestamp":"2026-10-20T10:15:30","status":403,"error":"No tiene permisos para realizar esta operación","codigo":"ACCESO_DENEGADO","ruta":"/v1/customers/clientes/buscar"}""";
    static final String ERROR_403_INACTIVA = """
            {"timestamp":"2026-10-20T10:15:30","status":403,"error":"La cuenta está desactivada","codigo":"CUENTA_INACTIVA","ruta":"/v1/customers/cartera/pagos"}""";
    static final String ERROR_404_CLIENTE = """
            {"timestamp":"2026-10-20T10:15:30","status":404,"error":"El usuario no tiene perfil de cliente","codigo":"NO_ENCONTRADO","ruta":"/v1/customers/clientes/yo"}""";
    static final String ERROR_404_PAGO = """
            {"timestamp":"2026-10-20T10:15:30","status":404,"error":"No existe un pago para esa compra o alquiler","codigo":"NO_ENCONTRADO","ruta":"/v1/customers/cartera/pagos"}""";
    static final String ERROR_409_NIT = """
            {"timestamp":"2026-10-20T10:15:30","status":409,"error":"Ya existe un cliente con ese NIT","codigo":"NIT_DUPLICADO","ruta":"/v1/customers/clientes/yo"}""";
    static final String ERROR_409_CLAVE = """
            {"timestamp":"2026-10-20T10:15:30","status":409,"error":"La Clave-Idempotencia ya se usó con otros datos","codigo":"CLAVE_IDEMPOTENCIA_REUTILIZADA","ruta":"/v1/customers/cartera/pagos"}""";
    static final String ERROR_422_SALDO = """
            {"timestamp":"2026-10-20T10:15:30","status":422,"error":"El saldo de la cartera no alcanza para el pago","codigo":"SALDO_INSUFICIENTE","ruta":"/v1/customers/cartera/pagos"}""";
    static final String ERROR_422_PERFIL = """
            {"timestamp":"2026-10-20T10:15:30","status":422,"error":"Complete su NIT en el perfil para comprar o alquilar en línea","codigo":"PERFIL_INCOMPLETO","ruta":"/v1/customers/cartera/pagos"}""";

    static final String CLIENTE = """
            {"idUsuario":"0b5a7c1e-2d3f-4a5b-8c9d-0e1f2a3b4c5d","nombreCompleto":"Carlos Méndez","correo":"carlos.mendez@gmail.com","nit":"1234567K","dpi":"2456789010101","telefono":"55551234","direccion":"4a. Calle 12-35, Zona 1, Quetzaltenango","activo":true,"saldo":"350.00"}""";
    static final String CLIENTE_BUSQUEDA = """
            {"idUsuario":"0b5a7c1e-2d3f-4a5b-8c9d-0e1f2a3b4c5d","nombreCompleto":"Carlos Méndez","nit":"1234567K","activo":true}""";
    static final String CARTERA = """
            {"idCartera":"7c9e1a2b-3c4d-4e5f-9a0b-1c2d3e4f5a6b","saldo":"350.00","actualizadoEn":"2026-10-20T16:15:30Z"}""";
    static final String RECARGA = """
            {"idMovimiento":"9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a","tipo":"RECARGA","monto":"200.00","saldoPosterior":"350.00","tipoReferencia":null,"idReferencia":null,"fechaMovimiento":"2026-10-20","descripcion":"Recarga de saldo","creadoEn":"2026-10-20T16:15:30Z"}""";
    static final String MOVIMIENTOS = """
            {"contenido":[{"idMovimiento":"1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d","tipo":"PAGO","monto":"170.00","saldoPosterior":"180.00","tipoReferencia":"BOLETOS","idReferencia":"6f1c2a7e-3b4d-4e5f-8a9b-0c1d2e3f4a5b","fechaMovimiento":"2026-10-21","descripcion":"2 boletos Xela → Capital","creadoEn":"2026-10-21T14:02:11Z"}],"pagina":0,"tamano":20,"totalElementos":1,"totalPaginas":1}""";
    static final String PAGO = """
            {"idPago":"1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d","saldoPosterior":"180.00"}""";
    static final String PAGO_CONSULTA = """
            {"idPago":"1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d","monto":"170.00","fechaPago":"2026-10-21"}""";
}
