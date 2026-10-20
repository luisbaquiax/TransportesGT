package org.transportsgt.ticketservice.controllers;

final class EjemplosApi {

    private EjemplosApi() {
    }

    static final String ERROR_400 = """
            {"timestamp":"2026-10-20T10:15:30","status":400,"error":"El asiento 12 está repetido","codigo":"VALIDACION_FALLIDA","ruta":"/v1/tickets/boletos/compras"}""";
    static final String ERROR_401 = """
            {"timestamp":"2026-10-20T10:15:30","status":401,"error":"Debe iniciar sesión: el token falta, no es válido o expiró","codigo":"NO_AUTENTICADO","ruta":"/v1/tickets/boletos/viajes"}""";
    static final String ERROR_403_SUCURSAL = """
            {"timestamp":"2026-10-20T10:15:30","status":403,"error":"El viaje es de otra sucursal","codigo":"SIN_PERMISO","ruta":"/v1/tickets/boletos/ventas-mostrador"}""";
    static final String ERROR_403_INACTIVA = """
            {"timestamp":"2026-10-20T10:15:30","status":403,"error":"La cuenta está desactivada","codigo":"CUENTA_INACTIVA","ruta":"/v1/tickets/boletos/compras"}""";
    static final String ERROR_404_VIAJE = """
            {"timestamp":"2026-10-20T10:15:30","status":404,"error":"El viaje no existe","codigo":"NO_ENCONTRADO","ruta":"/v1/tickets/boletos/viajes/5d1c2a7e-3b4d-4e5f-8a9b-0c1d2e3f4a5b/asientos"}""";
    static final String ERROR_404_COMPRA = """
            {"timestamp":"2026-10-20T10:15:30","status":404,"error":"La compra no existe","codigo":"NO_ENCONTRADO","ruta":"/v1/tickets/boletos/compras/6f1c2a7e-3b4d-4e5f-8a9b-0c1d2e3f4a5b"}""";
    static final String ERROR_409_ASIENTO = """
            {"timestamp":"2026-10-20T10:15:30","status":409,"error":"El asiento 12 ya está reservado o vendido","codigo":"ASIENTO_NO_DISPONIBLE","ruta":"/v1/tickets/boletos/compras"}""";
    static final String ERROR_409_VIAJE = """
            {"timestamp":"2026-10-20T10:15:30","status":409,"error":"El viaje ya salió","codigo":"VIAJE_NO_DISPONIBLE","ruta":"/v1/tickets/boletos/compras"}""";
    static final String ERROR_409_COMPRA = """
            {"timestamp":"2026-10-20T10:15:30","status":409,"error":"Ya existe una compra con ese idCompra y otros datos","codigo":"COMPRA_DUPLICADA","ruta":"/v1/tickets/boletos/compras"}""";
    static final String ERROR_422_SALDO = """
            {"timestamp":"2026-10-20T10:15:30","status":422,"error":"El saldo de la cartera no alcanza para el pago","codigo":"SALDO_INSUFICIENTE","ruta":"/v1/tickets/boletos/compras"}""";
    static final String ERROR_422_PERFIL = """
            {"timestamp":"2026-10-20T10:15:30","status":422,"error":"Complete su NIT en el perfil para comprar en línea","codigo":"PERFIL_INCOMPLETO","ruta":"/v1/tickets/boletos/compras"}""";

    static final String VIAJES = """
            [{"idViaje":"5d1c2a7e-3b4d-4e5f-8a9b-0c1d2e3f4a5b","idRuta":"7a8b9c0d-1e2f-4a3b-8c4d-5e6f7a8b9c0d","idSucursalOrigen":"10000000-0000-4000-8000-000000000001","nombreOrigen":"Quetzaltenango","nombreDestino":"Ciudad de Guatemala","fechaHoraSalida":"2026-10-25T06:00:00-06:00","fechaHoraLlegadaEstimada":"2026-10-25T10:30:00-06:00","precioBoleto":"85.00","placaBus":"P-123ABC","capacidad":45,"asientosDisponibles":43}]""";
    static final String MAPA_ASIENTOS = """
            {"idViaje":"5d1c2a7e-3b4d-4e5f-8a9b-0c1d2e3f4a5b","capacidad":45,"ocupados":[1,4,12]}""";
    static final String ASIENTO_MAXIMO = """
            {"idViaje":"5d1c2a7e-3b4d-4e5f-8a9b-0c1d2e3f4a5b","asientoMaximo":12}""";
    static final String COMPRA = """
            {"idCompra":"6f1c2a7e-3b4d-4e5f-8a9b-0c1d2e3f4a5b","idViaje":"5d1c2a7e-3b4d-4e5f-8a9b-0c1d2e3f4a5b","canal":"EN_LINEA","estado":"CONFIRMADA","asientos":[{"numeroAsiento":12,"codigo":"BOL-7K3Q9X2M","estado":"CONFIRMADO"},{"numeroAsiento":13,"codigo":"BOL-2HD8R4PZ","estado":"CONFIRMADO"}],"precioUnitario":"85.00","montoTotal":"170.00","nitComprador":"1234567K","nombreComprador":"Carlos Méndez","idPago":"1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d","fechaPago":"2026-10-20","reservaExpiraEn":"2026-10-20T16:25:00Z","motivoAnulacion":null,"creadoEn":"2026-10-20T16:15:00Z","confirmadoEn":"2026-10-20T16:15:02Z"}""";
    static final String COMPRA_RESERVADA = """
            {"idCompra":"6f1c2a7e-3b4d-4e5f-8a9b-0c1d2e3f4a5b","idViaje":"5d1c2a7e-3b4d-4e5f-8a9b-0c1d2e3f4a5b","canal":"EN_LINEA","estado":"RESERVADA","asientos":[{"numeroAsiento":12,"codigo":"BOL-7K3Q9X2M","estado":"RESERVADO"}],"precioUnitario":"85.00","montoTotal":"85.00","nitComprador":"1234567K","nombreComprador":"Carlos Méndez","idPago":null,"fechaPago":"2026-10-20","reservaExpiraEn":"2026-10-20T16:25:00Z","motivoAnulacion":null,"creadoEn":"2026-10-20T16:15:00Z","confirmadoEn":null}""";
}
