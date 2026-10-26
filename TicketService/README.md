# servicio-boletos (TicketService)

Venta de boletos de TransportesGT: consulta de viajes a la venta y mapa de asientos, compra en línea pagada con la
cartera (saga con Cartera) y venta de mostrador. Proyecta los viajes que publica Viajes y los perfiles que publica
Cartera, y publica sus eventos por **outbox transaccional** (librería `EventosComunesTrasnportesGt` `v2.1.1`) en el
exchange `transporte.eventos`.

| | |
|---|---|
| Puerto | `8084` |
| Ruta base | `/v1/tickets` (`server.servlet.context-path`; el gateway reenvía `/v1/tickets/**` sin reescribir) |
| Swagger | <http://localhost:8084/v1/tickets/swagger-ui.html> |
| OpenAPI | <http://localhost:8084/v1/tickets/v3/api-docs> |
| Health | <http://localhost:8084/v1/tickets/actuator/health> |
| Base de datos | `bd_boletos` (PostgreSQL), migración Flyway `src/main/resources/db/migration/V1__esquema_inicial.sql` (modelo v4 §6) |

## Ejecutar en local

```bash
# 1. Infraestructura (PostgreSQL + RabbitMQ)
cd ../infra && docker compose up -d

# 2. Variables (archivo .env en esta carpeta; ver tabla)
# 3. Arrancar (perfil dev por defecto)
./mvnw spring-boot:run
```

Para comprar en línea también debe estar levantado CustomerWalletService (puerto `8085`).

### Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | sí | Conexión a `bd_boletos` |
| `RABBITMQ_URL` | sí | `amqp://usuario:clave@host:5672` (en la nube, `amqps://`) |
| `JWT_SECRET` | sí | Secreto HS256 en **Base64**, el mismo de Identidad y del gateway. Este servicio solo **valida** el token |
| `JWT_ISSUER` | no | Emisor esperado del claim `iss`. Por defecto `servicio-identidad` |
| `CLAVE_INTERNA` | sí | Secreto de `X-Clave-Interna`: lo envía el job al consultar Cartera y lo acepta `GET /boletos/viajes/{id}/asiento-maximo` (Viajes). El servicio no arranca sin ella |
| `CARTERA_URL` | no | Ruta base de Cartera. Por defecto `http://localhost:8085/v1/customers` |
| `SERVER_PORT` | no | Por defecto `8084` |
| `SPRING_PROFILES_ACTIVE` | no | `dev` (por defecto) o `prod` |

Reglas configurables en `application.properties`: `boletos.compra.max-asientos` (10), `boletos.reserva.duracion`
(10 min), `boletos.recuperacion.antiguedad` (2 min) y `boletos.recuperacion.intervalo-ms` (60 000). Timeout de
Cartera: `cartera.timeout` (3 s). Reintentos y circuit breaker: instancias `cartera` de Resilience4j.

## Datos de prueba

El servicio **no tiene datos iniciales**: los viajes llegan por `ViajeProgramado` y los perfiles (NIT y nombre del
comprador) por `PerfilClienteActualizado`. Sin Viajes ni Cartera levantados, publíquelos a mano desde la consola de
RabbitMQ (<http://localhost:15672>, `guest` / `guest`):

1. *Exchanges* → `transporte.eventos` → *Publish message*.
2. *Routing key*: la indicada abajo.
3. *Properties*: `content_type = application/json`.
4. *Payload*: el JSON de abajo. Cambie `idEvento` en cada envío y suba `versionAgregado` si vuelve a publicar el
   mismo viaje o perfil (una versión menor o igual a la guardada se ignora).

**Viaje a la venta** — routing key `viajes.viaje.programado`. Ponga una `fechaHoraSalida` futura; `idSucursalOrigen`
debe ser la sucursal del cajero que venderá en mostrador.

```json
{
  "idEvento": "7c1d2e3f-1111-4a2b-9c3d-000000000001",
  "tipoEvento": "ViajeProgramado",
  "versionEvento": 1,
  "ocurridoEn": "2026-10-20T14:32:10Z",
  "servicioProductor": "servicio-viajes",
  "idAgregado": "5d1c2a7e-3b4d-4e5f-8a9b-0c1d2e3f4a5b",
  "versionAgregado": 0,
  "idCorrelacion": "7c1d2e3f-2222-4a2b-9c3d-000000000001",
  "datos": {
    "idViaje": "5d1c2a7e-3b4d-4e5f-8a9b-0c1d2e3f4a5b",
    "tipoViaje": "REGULAR",
    "estado": "PROGRAMADO",
    "idRuta": "3a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d",
    "idSucursalOrigen": "10000000-0000-4000-8000-000000000001",
    "nombreOrigen": "Quetzaltenango",
    "nombreDestino": "Ciudad de Guatemala",
    "idAlquiler": null,
    "fechaHoraSalida": "2026-10-25T08:00:00-06:00",
    "fechaHoraLlegadaEstimada": "2026-10-25T12:00:00-06:00",
    "precioBoleto": "85.00",
    "bus": { "idBus": "8e7d6c5b-4a39-4281-9f0e-1d2c3b4a5f6e", "placa": "P-123ABC", "capacidad": 40 },
    "chofer": { "idChofer": "2f3e4d5c-6b7a-4891-8a0b-1c2d3e4f5a6b", "nombreCompleto": "Juan Pérez" }
  }
}
```

Compruebe: `SELECT * FROM viaje_ref WHERE id = '5d1c2a7e-3b4d-4e5f-8a9b-0c1d2e3f4a5b';`. Un viaje
`ALQUILER_PRIVADO` se ignora.

**Perfil del comprador** — routing key `cartera.cliente.actualizado`. `idUsuario` debe ser el `sub` del JWT que
usará; sin NIT, la compra en línea responde 422 `PERFIL_INCOMPLETO`.

```json
{
  "idEvento": "7c1d2e3f-3333-4a2b-9c3d-000000000001",
  "tipoEvento": "PerfilClienteActualizado",
  "versionEvento": 1,
  "ocurridoEn": "2026-10-20T14:32:10Z",
  "servicioProductor": "servicio-clientes",
  "idAgregado": "00000000-0000-4000-8000-000000000006",
  "versionAgregado": 1,
  "idCorrelacion": "7c1d2e3f-4444-4a2b-9c3d-000000000001",
  "datos": {
    "idUsuario": "00000000-0000-4000-8000-000000000006",
    "nit": "1234567K",
    "nombreCompleto": "Carlos Méndez",
    "activo": true
  }
}
```

Compruebe: `SELECT * FROM cliente_ref WHERE id_usuario = '00000000-0000-4000-8000-000000000006';`.
Un mensaje con JSON inválido o datos imposibles aparece en `boletos.viajes.dlq` o `boletos.cartera.dlq`.

## Endpoints

| Método y ruta | Quién | Respuesta | Eventos |
|---|---|---|---|
| `GET /boletos/viajes?idOrigen=&idRuta=&destino=&fecha=` | autenticado | Viajes `PROGRAMADO`, no eliminados, con bus y salida futura, con `asientosDisponibles`. `destino` busca por texto; `fecha` es el día en hora de Guatemala | — |
| `GET /boletos/viajes/{id}/asientos` | autenticado | `{ idViaje, capacidad, ocupados }`. 404 si no existe o se eliminó | — |
| `GET /boletos/viajes/{id}/asiento-maximo` | autenticado **o** `X-Clave-Interna` (Viajes) | `{ idViaje, asientoMaximo }` (0 si no hay boletos activos). Clave incorrecta → 401 | — |
| `POST /boletos/compras` | autenticado | `{ idCompra, idViaje, asientos, fechaPago }`. 201 `CONFIRMADA` + `Location` · 200 si el `idCompra` ya estaba confirmado · 202 `RESERVADA` + `Location` si Cartera no respondió · 400 asientos inválidos · 403 `CUENTA_INACTIVA` · 409 `ASIENTO_NO_DISPONIBLE` / `VIAJE_NO_DISPONIBLE` / `COMPRA_DUPLICADA` / `COMPRA_ANULADA` · 422 `SALDO_INSUFICIENTE` / `PERFIL_INCOMPLETO` | VentaBoletosRegistrada |
| `POST /boletos/ventas-mostrador` | CAJERO, ADMIN_SUCURSAL | `{ idCompra, idViaje, asientos, nitComprador?, nombreComprador?, fechaPago }`. 201 + `Location` · 200 si se repite · 403 `SIN_PERMISO` si el viaje sale de otra sucursal | VentaBoletosRegistrada |
| `GET /boletos/compras/mias?pagina=&tamano=` | autenticado | Compras en línea del usuario **y** ventas de mostrador con el NIT de su perfil (también las anteriores a la cuenta) | — |
| `GET /boletos/compras/{id}` | autenticado | El comprador ve las suyas; SUC y CAJ, las de su sucursal; SIS, todas. Si no le corresponde → 404 | — |
| `GET /boletos/compras?idViaje=&estado=&pagina=&tamano=` | CAJERO, ADMIN_SUCURSAL | Compras de su sucursal, más recientes primero | — |
| `GET /boletos/compras/{id}/pdf` | autenticado | Mismos permisos que el detalle. PDF adjunto con el viaje, el comprador y **un QR por boleto** (contenido: el `codigo`). Compra no `CONFIRMADA` → 409 `COMPRA_NO_CONFIRMADA` | — |
| `GET /boletos/viajes/{id}/pasajeros` | CAJERO, ADMIN_SUCURSAL (sucursal de origen) | Boletos `CONFIRMADO` por asiento con `abordadoEn`, y los totales `boletos` / `abordados` | — |
| `POST /boletos/viajes/{id}/abordajes` | CAJERO, ADMIN_SUCURSAL (sucursal de origen) | `{ codigo }` (QR o a mano). 201 · 200 si ya había abordado · 404 código inexistente · 409 `BOLETO_NO_VALIDO` (anulado o de otro viaje) · 409 `VIAJE_NO_DISPONIBLE` (ya salió) | — |

Errores: `{ timestamp, status, error, codigo, ruta }` con un `codigo` estable (lista en Swagger).

## Eventos

### Consumidos

| Cola | Llave | Evento | Efecto |
|---|---|---|---|
| `boletos.viajes.q` | `viajes.viaje.programado` | ViajeProgramado | *Upsert* de `viaje_ref` si `versionAgregado` es mayor (solo `REGULAR`) |
| `boletos.viajes.q` | `viajes.viaje.actualizado` | ViajeActualizado | Igual que Programado (puede llegar antes) |
| `boletos.viajes.q` | `viajes.viaje.eliminado` | ViajeEliminado | Marca el viaje eliminado y anula sus compras `RESERVADA` y `CONFIRMADA` |
| `boletos.cartera.q` | `cartera.cliente.actualizado` | PerfilClienteActualizado | *Upsert* de `cliente_ref` (NIT, nombre, activo) |

Las colas y enlaces los declara la librería a partir de los `ManejadorEvento` registrados.

### Publicados

| Evento | Llave | `idAgregado` / versión | Cuándo |
|---|---|---|---|
| VentaBoletosRegistrada | `boletos.venta.registrada` | `idCompra` / `compra.version` | Compra en línea confirmada o venta de mostrador; un evento por compra con todos sus asientos |
| BoletosAnulados | `boletos.venta.anulada` | `idCompra` / `compra.version` | Compra anulada por ViajeEliminado. Si el pago llega después de anular, se publica **otro** con el `idPago` para que Cartera reembolse |

Una compra que nunca se confirmó (saldo insuficiente o reserva vencida) se anula **sin** evento: no hubo venta ni cobro.

## Reglas importantes

- **Asientos.** El índice parcial `uq_asiento_activo (id_viaje, numero_asiento) WHERE estado IN ('RESERVADO',
  'CONFIRMADO')` es la garantía real contra la doble venta: de dos compras simultáneas del mismo asiento, una recibe
  409. El servicio valida antes (1..capacidad, sin repetidos, máximo 10, no ocupados) para dar un mensaje claro, y el
  trigger `trg_boleto_validar_asiento` queda de respaldo.
- **Saga en línea.** Tx1 reserva (compra `RESERVADA`, boletos `RESERVADO`, vence en 10 min) → cobro a Cartera **fuera
  de transacción** → Tx2 confirma (con `VentaBoletosRegistrada`) o libera. `idCompra` es la clave de idempotencia de
  punta a punta: un reenvío no crea otra compra ni cobra dos veces.
- **Cartera.** `RestClient` con timeout de 3 s; Resilience4j reintenta (3 intentos, 500 ms ×2) solo ante fallas de
  red y 5xx, y el circuit breaker abre al 50 % de fallas sobre 10 llamadas. Un 4xx es un rechazo de negocio: se
  propagan su status y su `codigo`, y se liberan los asientos. Sin respuesta, la compra queda `RESERVADA` (202).
- **Recuperación.** Cada minuto el job revisa las compras `RESERVADA` con 2 min o más y consulta
  `GET /cartera/pagos?tipoReferencia=BOLETOS&idReferencia=` con `X-Clave-Interna`: con pago confirma; sin pago y con la
  reserva vencida libera; si Cartera no responde, espera al siguiente ciclo. Cada compra va en su propia transacción.
- **ViajeEliminado.** El viaje, las compras anuladas, sus BoletosAnulados y `evento_procesado` se confirman en una
  sola transacción.
- **Abordaje.** Lo registra el personal de la sucursal de origen mientras el viaje está `PROGRAMADO` (al registrar
  la salida pasa a `EN_TRANSITO`). Se guarda en `boleto.abordado_en` / `abordado_por`: el boleto sigue `CONFIRMADO`,
  así que no cambian el índice de asientos, los reportes ni la anulación. Un boleto abordado no se anula desde Boletos
  (no hay endpoint para eso); solo si Viajes elimina el viaje antes de la salida, y conserva el registro. El boleto se
  bloquea (`FOR UPDATE`) para que dos lectores no lo registren a la vez. No publica eventos.
- **PDF.** OpenPDF arma el documento y ZXing el QR (el `codigo` del boleto, `BOL-` + 8 caracteres de `SecureRandom`).
- **Mostrador.** Sin pago de cartera (`idPago = null`). NIT normalizado con `Nit.normalizar()`; vacío o `CF` →
  consumidor final.
- **Outbox.** Cada evento se guarda en `outbox_evento` en la misma transacción que el cambio, después de `flush()`.
  La librería lo publica cada segundo y borra lo publicado hace más de 24 h.
- **Consumidor.** Reintentos: 1 intento + 3 con espera de 1, 2 y 4 s; JSON inválido o dato imposible → DLQ inmediato.
- **Logs.** Nunca llevan el payload de un evento; solo `tipoEvento`, `idEvento` e `idCorrelacion`.
- **Correlación.** `X-Correlation-Id` se copia a los eventos, a los logs y a las llamadas a Cartera.

## Pruebas

```bash
./mvnw test     # unitarias (*Test) + JaCoCo ≥ 85 %
./mvnw verify   # + integración (*IT, Testcontainers: requiere Docker) + JaCoCo de integración ≥ 60 %
```

- **Unitarias** (JUnit 5 + Mockito, `@Nested` por método, fixtures en `fixture/`): servicios y saga, adaptador de
  Cartera con los registros reales de Resilience4j, manejadores de eventos, `EventosBoletos`, job, controladores,
  handler de errores, filtro de clave interna, utilidades y `Specification`.
- **Integración** con PostgreSQL 16 y RabbitMQ reales y Cartera simulada con WireMock (`integracion/`):
  - compra confirmada con el cobro correcto y el evento en el outbox con su llave;
  - tres compras simultáneas del mismo asiento (una 201, dos 409, un solo cobro);
  - `idCompra` repetido (una compra, un cobro) y con otros datos (409);
  - saldo insuficiente (422 y asientos liberados);
  - Cartera caída (202) y el job que confirma o libera;
  - viaje eliminado durante el cobro (segundo BoletosAnulados con `idPago`);
  - ViajeProgramado duplicado, versión vieja, Actualizado antes que Programado, alquiler ignorado, JSON inválido a la DLQ;
  - ViajeEliminado con compras en línea y de mostrador;
  - viaje no `PROGRAMADO` o que ya salió (409);
  - historial por NIT con una venta de mostrador anterior a la cuenta y venta desde otra sucursal (403);
  - `asiento-maximo` con JWT, con la clave correcta y con una incorrecta;
  - esquema de Flyway y trigger de capacidad del bus.
- Reportes: `target/site/jacoco/index.html` (unitarias) y `target/site/jacoco-it/index.html` (integración).

## Postman

`../../docs/postman/boletos.postman_collection.json` usa `{{baseUrl}}`, `{{token}}`, `{{tokenCajero}}`,
`{{claveInterna}}` e `{{idViaje}}` (lo guarda *Viajes a la venta*). Cada compra nueva genera `idCompra` con
`{{$guid}}`; *Repetir la última compra* reenvía la misma para probar la idempotencia.

## Manual técnico

`../../docs/manual-tecnico-boletos.md`: diagramas de componentes, saga de compra con el job de recuperación,
eliminación de un viaje y despliegue.
