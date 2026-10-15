# servicio-clientes (CustomerWalletService)

Clientes y Cartera de TransportesGT: perfil comercial (NIT, DPI, teléfono, dirección), cartera digital, recargas,
pagos de compras y alquileres, y reembolsos. Consume eventos de Identidad, Boletos y Alquileres, y publica los
suyos por **outbox transaccional** (librería `EventosComunesTrasnportesGt` `v2.1.1`) en el exchange
`transporte.eventos`.

| | |
|---|---|
| Puerto | `8085` |
| Ruta base | `/v1/customers` (`server.servlet.context-path`; el gateway reenvía `/v1/customers/**` sin reescribir) |
| Swagger | <http://localhost:8085/v1/customers/swagger-ui.html> |
| OpenAPI | <http://localhost:8085/v1/customers/v3/api-docs> |
| Health | <http://localhost:8085/v1/customers/actuator/health> |
| Base de datos | `bd_clientes` (PostgreSQL), migración Flyway `src/main/resources/db/migration/V1__esquema_inicial.sql` (modelo v4 §5) |

## Ejecutar en local

```bash
# 1. Infraestructura (PostgreSQL + RabbitMQ)
cd ../infra && docker compose up -d

# 2. Variables (archivo .env en esta carpeta; ver tabla)
# 3. Arrancar (perfil dev por defecto)
./mvnw spring-boot:run
```

### Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | sí | Conexión a `bd_clientes` |
| `RABBITMQ_URL` | sí | `amqp://usuario:clave@host:5672` (en la nube, `amqps://`) |
| `JWT_SECRET` | sí | Secreto HS256 en **Base64**, el mismo de Identidad y del gateway. Este servicio solo **valida** el token |
| `JWT_ISSUER` | no | Emisor esperado del claim `iss`. Por defecto `servicio-identidad` |
| `CLAVE_INTERNA` | sí | Secreto de la cabecera `X-Clave-Interna` (jobs de recuperación de Boletos y Alquileres). El servicio no arranca sin ella |
| `SERVER_PORT` | no | Por defecto `8085` |
| `SPRING_PROFILES_ACTIVE` | no | `dev` (por defecto) o `prod` |

> `JWT_EXPIRATION_MS` del `.env` no se usa aquí: la vigencia del token la fija Identidad.

## Datos de prueba

El servicio **no tiene datos iniciales propios**: cada cliente (con su cartera en saldo 0) nace al consumir
`UsuarioCreado`, de **cualquier rol**, incluido el administrador de sistema sembrado en Identidad.

- **Con Identidad levantada:** ejecute `POST /v1/identity/admin/republicar-eventos` (token de ADMIN_SISTEMA). Publica
  `UsuarioCreado` de todos los usuarios existentes y este servicio crea sus clientes.
- **Sin Identidad:** publique a mano un `UsuarioCreado` desde la consola de RabbitMQ
  (<http://localhost:15672>, `guest` / `guest`):
  1. *Exchanges* → `transporte.eventos` → *Publish message*.
  2. *Routing key*: `identidad.usuario.creado`.
  3. *Properties*: `content_type = application/json`.
  4. *Payload* (cambie `idEvento` en cada envío; `idUsuario` debe ser el `sub` del JWT que usará):

```json
{
  "idEvento": "6b0c1f7e-1111-4a2b-9c3d-000000000001",
  "tipoEvento": "UsuarioCreado",
  "versionEvento": 1,
  "ocurridoEn": "2026-10-20T14:32:10Z",
  "servicioProductor": "servicio-identidad",
  "idAgregado": "00000000-0000-4000-8000-000000000006",
  "versionAgregado": 0,
  "idCorrelacion": "6b0c1f7e-2222-4a2b-9c3d-000000000001",
  "datos": {
    "idUsuario": "00000000-0000-4000-8000-000000000006",
    "correo": "cliente@transportes.gt",
    "nombreCompleto": "Carlos Méndez",
    "rol": "CLIENTE",
    "idSucursal": null,
    "activo": true
  }
}
```

Compruebe en la base: `SELECT * FROM cliente WHERE id_usuario = '00000000-0000-4000-8000-000000000006';`.
Si el JSON es inválido, el mensaje aparece en la cola `cartera.identidad.dlq`.

## Endpoints

| Método y ruta | Quién | Respuesta | Eventos |
|---|---|---|---|
| `GET /clientes/yo` | autenticado | Perfil y saldo | — |
| `PUT /clientes/yo` | autenticado | `{ nit, dpi, telefono, direccion }`. NIT normalizado; `CF` → 400. NIT/DPI repetido → 409 | PerfilClienteActualizado (si cambió el NIT) |
| `GET /clientes/buscar?nit=` | CAJERO, ADMIN_SUCURSAL | Cliente para la venta de mostrador; 404 si no existe | — |
| `GET /cartera/yo` | autenticado | Saldo | — |
| `POST /cartera/recargas` | autenticado | `Clave-Idempotencia` obligatoria. 201 nueva · 200 repetida · 409 clave con otros datos · 403 `CUENTA_INACTIVA` | CarteraRecargada |
| `GET /cartera/yo/movimientos?tipo=&desde=&hasta=&pagina=&tamano=` | autenticado | Página `{ contenido, pagina, tamano, totalElementos, totalPaginas }`, más reciente primero | — |
| `POST /cartera/pagos` | **Interno** (Boletos, Alquileres) con el JWT del usuario reenviado | `Clave-Idempotencia` = `idReferencia`. 201 `{ idPago, saldoPosterior }` + `Location` · 200 si ya existía · 409 con otros datos · 422 `SALDO_INSUFICIENTE` / `PERFIL_INCOMPLETO` · 403 `CUENTA_INACTIVA` | — |
| `GET /cartera/pagos?tipoReferencia=&idReferencia=` | **Interno**: JWT (solo sus pagos) o `X-Clave-Interna` (cualquiera) | 200 `{ idPago, monto, fechaPago }` o 404 | — |

Los reembolsos **no tienen endpoint**: nacen al consumir `BoletosAnulados` y `AlquilerCancelado`.
`GET /cartera/pagos` con `X-Clave-Interna` no se publica en el gateway: los jobs llaman directo al puerto 8085.

### Errores

Todas las respuestas de error (incluidos 401 y 403 de los filtros de seguridad) tienen el mismo formato:

```json
{ "timestamp": "2026-10-20T10:15:30", "status": 422, "error": "El saldo de la cartera no alcanza para el pago",
  "codigo": "SALDO_INSUFICIENTE", "ruta": "/v1/customers/cartera/pagos" }
```

| HTTP | `codigo` |
|---|---|
| 400 | `VALIDACION_FALLIDA` (campos, NIT inválido o `CF`) · `SOLICITUD_INVALIDA` (falta `Clave-Idempotencia`, clave distinta de `idReferencia`, rango de fechas) |
| 401 | `NO_AUTENTICADO` (sin token, token inválido o `X-Clave-Interna` incorrecta) |
| 403 | `ACCESO_DENEGADO` (rol) · `CUENTA_INACTIVA` |
| 404 | `NO_ENCONTRADO` |
| 409 | `NIT_DUPLICADO` · `DPI_DUPLICADO` · `CLAVE_IDEMPOTENCIA_REUTILIZADA` |
| 422 | `SALDO_INSUFICIENTE` · `PERFIL_INCOMPLETO` |

## Eventos

### Consumidos (una cola por productor, cada una con su DLQ en `transporte.eventos.dlx`)

| Cola | Llave (exacta, sin comodín) | Evento | Efecto |
|---|---|---|---|
| `cartera.identidad.q` | `identidad.usuario.creado` | UsuarioCreado | Crea cliente y cartera en saldo 0 (si no existe) |
| `cartera.identidad.q` | `identidad.usuario.actualizado` | UsuarioActualizado | Si `versionAgregado > version_usuario`: nombre, correo y `activo`. Si el cliente no existe, lo crea |
| `cartera.boletos.q` | `boletos.venta.anulada` | BoletosAnulados | Reembolso solo si `canal = EN_LINEA` |
| `cartera.alquileres.q` | `alquileres.alquiler.cancelado` | AlquilerCancelado | Reembolso solo si `idPago != null` |

La cola de Identidad **no** usa `identidad.usuario.*`: traería `EnlaceContrasenaEmitido`, que lleva un token en claro.
Las colas y enlaces los declara la librería a partir de los `ManejadorEvento` registrados.

### Publicados

| Evento | Llave | `idAgregado` / versión | Cuándo |
|---|---|---|---|
| PerfilClienteActualizado | `cartera.cliente.actualizado` | `id_usuario` / `cliente.version` | Al crear el cliente (con `nit` nulo) y cuando cambian el nombre, el NIT o `activo` (no por un cambio de correo) |
| CarteraRecargada | `cartera.cartera.recargada` | `id` de la cartera / `cartera.version` | Recarga nueva (con `saldoPosterior` y `fechaRecarga`) |
| CarteraReembolsada | `cartera.cartera.reembolsada` | `id` de la cartera / `cartera.version` | Reembolso aplicado (con `saldoPosterior` y `motivo`) |

Los pagos **no** publican evento (el catálogo no define uno).

## Reglas importantes

- **Concurrencia.** Recargas, pagos y reembolsos bloquean la cartera con `SELECT … FOR UPDATE` en la **primera**
  lectura y calculan `saldo_posterior` en Java con ese bloqueo. Dos pagos simultáneos que juntos superan el saldo:
  uno responde 201 y el otro 422. El `CHECK (saldo >= 0)` queda de respaldo.
- **Idempotencia.** Recarga: misma clave, misma cartera y mismo monto → 200 con la original; si cambia algo, 409.
  Pago: el índice `uq_movimiento_referencia` garantiza un solo PAGO y un solo REEMBOLSO por compra o alquiler.
- **Reembolso.** Se busca el PAGO original por `idPago` (o por la referencia si no viene) y se devuelve su monto.
  Sin PAGO, el evento se marca procesado y no hace nada. Se aplica **aunque el cliente esté inactivo**. Su fecha es la
  de `canceladoEn` (alquiler) u `ocurridoEn` (boletos) en hora de Guatemala. Si `idPago` apunta a otra referencia, el
  evento va a la DLQ.
- **NIT.** Se normaliza con `Nit.normalizar()` (mayúsculas, sin guiones ni espacios). `CF` se rechaza en el perfil:
  solo existe en el mostrador.
- **Libro inmutable.** `movimiento_cartera` no se modifica ni se elimina (trigger `trg_movimiento_inmutable`).
- **Outbox.** Cada evento se guarda en `outbox_evento` en la misma transacción que el cambio, después de `flush()`,
  para que `versionAgregado` sea la versión ya incrementada. La librería lo publica cada segundo y **borra a diario lo
  publicado hace más de 24 h** (3:30 a. m.): este servicio no necesita un job propio.
- **Consumidor.** Cada efecto, su fila en `evento_procesado` y el evento de salida van en una transacción. Reintentos:
  1 intento + 3 con espera de 1, 2 y 4 s; JSON inválido o dato imposible → DLQ inmediato.
- **Logs.** Nunca llevan el payload de un evento; solo `tipoEvento`, `idEvento` e `idCorrelacion`.
- **Correlación.** La cabecera `X-Correlation-Id` (o una nueva) se copia a cada evento y a los logs.

## Pruebas

```bash
./mvnw test     # unitarias (*Test) + JaCoCo ≥ 85 %
./mvnw verify   # + integración (*IT, Testcontainers: requiere Docker) + JaCoCo de integración ≥ 60 %
```

- **Unitarias** (JUnit 5 + Mockito, `@Nested` por método, fixtures en `fixture/`): servicios, manejadores de eventos,
  `EventosCartera`, controladores, handler de errores, filtro de clave interna, utilidades y `Specification`.
- **Integración** con PostgreSQL 16 y RabbitMQ reales (`integracion/`):
  - dos pagos simultáneos cuando el saldo alcanza para uno (201 y 422, saldo final correcto);
  - pago y recarga repetidos con la misma clave;
  - UsuarioCreado duplicado, UsuarioActualizado con versión vieja y Actualizado antes que Creado;
  - BoletosAnulados repetido (un solo reembolso), de MOSTRADOR (se ignora) y sin PAGO previo (se ignora);
  - reembolso a un cliente inactivo;
  - JSON inválido a la DLQ;
  - `CF` en el perfil (400) y búsqueda por rol;
  - `GET /cartera/pagos` con `X-Clave-Interna` correcta e incorrecta;
  - eventos en el outbox con la llave correcta;
  - trigger de inmutabilidad de `movimiento_cartera`.
- Reportes: `target/site/jacoco/index.html` (unitarias) y `target/site/jacoco-it/index.html` (integración).

## Postman

`../../docs/postman/clientes-cartera.postman_collection.json` usa `{{baseUrl}}`, `{{token}}`, `{{tokenCajero}}` y
`{{claveInterna}}`. Las claves de idempotencia se generan con `{{$guid}}`. En *Pagar una compra*, un script previo
usa el mismo GUID como `idReferencia`, porque la clave debe ser igual a la referencia.

## Manual técnico

`../../docs/manual-tecnico-clientes.md`: diagramas de tablas, componentes, saga de pago, reembolso y despliegue.
