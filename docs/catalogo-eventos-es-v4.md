# Catálogo de eventos — Plataforma de transporte extraurbano (versión en español, v4)

Contrato de mensajería asíncrona entre los 9 microservicios. En esta versión **los nombres de eventos, campos, colas y valores de enumeración están en español**, para que coincidan con el modelo de datos (`modelo-datos-postgresql-v4.md`). Este documento es la **fuente de verdad**: si un payload cambia, se cambia aquí primero.

- **Broker:** RabbitMQ (CloudAMQP en producción; decisión #8 en §6)
- **Serialización:** JSON (Jackson)
- **Entrega:** *at-least-once* → todo consumidor debe ser idempotente

### Cambios de la v4 respecto a la v3

- **Evento nuevo `PerfilClienteActualizado`** (Clientes y Cartera → Boletos y Alquileres): lleva NIT y nombre del cliente. Boletos y Alquileres lo guardan en `cliente_ref`; así no necesitan llamadas síncronas para saber a quién cobrar ni para armar el historial. Total: **32 tipos de evento**.
- **`ViajeActualizado` también al registrar salida y llegada**, con la versión incrementada (antes Boletos nunca se enteraba de que el viaje ya salió). Boletos rechaza ventas de viajes que no estén `PROGRAMADO` o cuya salida ya pasó.
- **`CarteraRecargada` y `CarteraReembolsada`** llevan `saldoPosterior`.
- **`AlquilerSolicitado`:** `fechaSalida` y `fechaRetorno` pasan a `fechaHoraSalida` y `fechaHoraRetorno` (fecha y hora con zona), igual que `AlquilerConfirmado`.
- **NIT normalizado** en todos los servicios (§1.3) y `'CF'` reservado para el mostrador.
- **Regla de desactivación de choferes** (§5): `GET /viajes/activos` acepta `idChofer`. Sigue siendo el mismo par síncrono Flota → Viajes.
- **Contrato REST de `POST /cartera/pagos`** y `GET /cartera/pagos` documentado en §5.
- Conteos: **27 de 31 pares (≈ 87 %)** son por eventos.

### Cambios de la v3 respecto a la v2

- **Cambio y restablecimiento de contraseña:** 2 eventos nuevos en Identidad, `EnlaceContrasenaEmitido` y `ContrasenaCambiada` (ambos consumidos por Notificaciones). Total: **31 tipos de evento**.
- **Decisiones cerradas** (tabla §6): #1 copia local de contactos · #3 el chofer tiene usuario solo para registrar salida y llegada · #6 boletos `ANULADO` · #7 cambio de bus validado con una consulta síncrona Viajes → Boletos · #8 RabbitMQ · #9 reportes por fechas de negocio.
- **Cuarta llamada síncrona:** Viajes → Boletos `GET /boletos/viajes/{idViaje}/asiento-maximo` (decisión #7). Proporción: 25 de 29 pares (≈ 86 %).

### Cambios de la v2 respecto a la v1

- **El salario del chofer cuenta como gasto** (decisión del equipo): `PagoChoferRegistrado` pasa a **P0** y ya no está "pendiente de confirmar".
- **Reembolso al eliminar un viaje** (decisión del equipo): se mantiene la política ya modelada.
- **Alquiler de ida y vuelta = un solo viaje.** `AlquilerCotizado` y `AlquilerConfirmado` llevan `fechaHoraLlegadaEstimada`, que la fija el administrador al cotizar.
- Decisiones #2, #4 y #5 de la tabla final quedan cerradas.
- Los gastos de taller son inmutables una vez registrados.

---

## 0. Equivalencias con la versión anterior

### Servicios

| Antes | Ahora | Prefijo de ruteo |
|---|---|---|
| Identity & Administration | `servicio-identidad` | `identidad` |
| Fleet | `servicio-flota` | `flota` |
| Routes & Trips | `servicio-viajes` | `viajes` |
| Ticketing | `servicio-boletos` | `boletos` |
| Customer & Wallet | `servicio-clientes` (Clientes y Cartera) | `cartera` |
| Private Rentals | `servicio-alquileres` | `alquileres` |
| Operational Costs | `servicio-costos` | `costos` |
| Notification | `servicio-notificaciones` | — (solo consume) |
| Reporting | `servicio-reportes` | — (solo consume) |

### Eventos

| Antes | Ahora |
|---|---|
| `UserCreated` / `UserUpdated` | `UsuarioCreado` / `UsuarioActualizado` |
| `BranchCreated` / `BranchUpdated` | `SucursalCreada` / `SucursalActualizada` |
| `BusRegistered` / `BusUpdated` | `BusRegistrado` / `BusActualizado` |
| `DriverRegistered` / `DriverUpdated` | `ChoferRegistrado` / `ChoferActualizado` |
| `RouteCreated` / `RouteUpdated` / `RouteDeleted` | `RutaCreada` / `RutaActualizada` / `RutaEliminada` |
| `TripScheduled` / `TripUpdated` / `TripDeleted` | `ViajeProgramado` / `ViajeActualizado` / `ViajeEliminado` |
| `TripDeparted` / `TripArrived` | `SalidaRegistrada` / `LlegadaRegistrada` |
| `TicketPurchased` / `TicketsVoided` | `VentaBoletosRegistrada` / `BoletosAnulados` |
| `WalletRecharged` / `WalletRefunded` | `CarteraRecargada` / `CarteraReembolsada` |
| `RentalRequested` / `RentalQuoted` / `RentalConfirmed` | `AlquilerSolicitado` / `AlquilerCotizado` / `AlquilerConfirmado` |
| *(nuevo)* | `AlquilerCancelado` |
| `FuelExpenseRecorded` | `GastoCombustibleRegistrado` |
| `DepreciationCalculated` | `DepreciacionCalculada` |
| `WorkshopExpenseRecorded` | `GastoTallerRegistrado` |
| `DepreciationRateChanged` | `TarifaDepreciacionCambiada` |
| `DriverPayRecorded` | `PagoChoferRegistrado` |

### Valores de enumeración

| Campo | Valores |
|---|---|
| `rol` | `ADMIN_SISTEMA`, `ADMIN_SUCURSAL`, `CAJERO`, `CHOFER`, `CLIENTE` |
| `estadoOperativo` (bus) | `DISPONIBLE`, `EN_TRANSITO`, `EN_TALLER`, `FUERA_DE_SERVICIO` |
| `tipoViaje` | `REGULAR`, `ALQUILER_PRIVADO` |
| `estado` (viaje) | `PROGRAMADO`, `PENDIENTE_ASIGNACION`, `EN_TRANSITO`, `FINALIZADO` |
| `canal` (venta) | `EN_LINEA`, `MOSTRADOR` |
| `tipo` (línea de taller) | `MANO_DE_OBRA`, `REPUESTO` |
| `estado` (compra) | `RESERVADA`, `CONFIRMADA`, `ANULADA` |
| `estado` (boleto) | `RESERVADO`, `CONFIRMADO`, `ANULADO` |
| `estado` (alquiler) | `SOLICITADO`, `COTIZADO`, `CONFIRMADO`, `FINALIZADO`, `CANCELADO` |
| `tipoReferencia` | `BOLETOS`, `ALQUILER` |
| `motivo` (anulación, cancelación, reembolso) | `VIAJE_ELIMINADO` |
| `proposito` (enlace de contraseña) | `RESTABLECIMIENTO`, `ACTIVACION` |
| `motivo` (contraseña cambiada) | `CAMBIO`, `RESTABLECIMIENTO`, `ACTIVACION` |

---

## 1. Convenciones

### 1.1 Sobre común

Todo evento viaja con este sobre. Solo cambia `datos`.

```json
{
  "idEvento": "uuid",
  "tipoEvento": "ViajeProgramado",
  "versionEvento": 1,
  "ocurridoEn": "2026-10-20T14:32:10Z",
  "servicioProductor": "servicio-viajes",
  "idAgregado": "uuid del idViaje",
  "versionAgregado": 3,
  "idCorrelacion": "uuid de la operación de negocio",
  "datos": { }
}
```

| Campo | Uso |
|---|---|
| `idEvento` | Clave de idempotencia. Único por evento. |
| `versionEvento` | Versión del contrato. Solo se agregan campos (compatible hacia atrás). Un cambio incompatible crea un nuevo tipo (`ViajeProgramadoV2`). |
| `idAgregado` / `versionAgregado` | Para eventos *instantánea*: el consumidor ignora un evento si su `versionAgregado` es menor o igual a la ya guardada (protege contra desorden y duplicados). |
| `idCorrelacion` | Se propaga de la petición HTTP original a todos los eventos derivados. Permite seguir un flujo completo en los logs. |

### 1.2 Tipos de evento

| Tipo | Qué lleva | Cómo se consume |
|---|---|---|
| **Instantánea** (estado) | Estado completo de la entidad (`BusActualizado`, `ViajeProgramado`, ...) | *Upsert* en la proyección local. `Registrado`/`Creado` y `Actualizado` se tratan igual. |
| **Hecho** | Algo que ocurrió una sola vez (`VentaBoletosRegistrada`, `LlegadaRegistrada`, ...) | Efecto de negocio una sola vez, protegido por idempotencia. |

### 1.3 Formatos

- **IDs:** UUID (string).
- **Dinero:** string decimal con 2 decimales, moneda GTQ (`"125.00"`). Nunca `float`.
- **Fechas elegidas por el usuario** (`fechaPago`, `fechaServicio`, `fechaRecarga`, ...): `LocalDate` ISO (`2026-10-20`). **Son distintas de `ocurridoEn`**, que es la hora técnica del evento. El enunciado exige que el usuario pueda elegir las fechas, así que los payloads llevan la fecha de negocio, no la del servidor.
- **Fecha-hora de negocio** (salida y llegada reales): ISO-8601 con zona (`2026-10-20T08:00:00-06:00`).
- **NIT:** siempre normalizado (`Nit.normalizar()` de `eventos-comun`): mayúsculas y sin guiones ni espacios (`1234567-k` → `1234567K`). `CF` significa consumidor final y solo existe en ventas y alquileres de mostrador; un perfil de cliente no puede tener NIT `CF`.
- `?` después del nombre de campo = opcional (puede ser `null`).

### 1.4 Topología RabbitMQ

- Un único **exchange de tipo topic:** `transporte.eventos`.
- **Llave de ruteo:** `{servicio}.{agregado}.{acción}` → `viajes.viaje.programado`, `flota.bus.actualizado`, `boletos.venta.registrada`.
- **Una cola por (consumidor, productor):** `boletos.viajes.q` enlazada con `viajes.viaje.*`. Cada consumidor se suscribe solo a lo que necesita y una falla no afecta a los demás.
- **Cola de errores por cada cola:** `boletos.viajes.dlq` (mediante el exchange de mensajes fallidos `transporte.eventos.dlx`).

```
Productor ──> [transporte.eventos] ──llave──> [consumidor.productor.q] ──> Consumidor
                                                      │ (falla tras reintentos)
                                                      └──> [consumidor.productor.dlq]
```

### 1.5 Confiabilidad (lo que evalúa el enunciado)

| Mecanismo | Dónde | Regla |
|---|---|---|
| **Outbox transaccional** | Productor | El evento se guarda en la tabla `outbox_evento` **en la misma transacción** que el cambio de negocio. Un *poller* (`@Scheduled`) lo publica y marca `publicado_en`. Garantiza que no haya cambio sin evento. |
| **Consumidor idempotente** | Consumidor | Tabla `evento_procesado(id_evento, consumidor)` con clave primaria compuesta. Se inserta **en la misma transacción** que el efecto. Si falla por duplicado → se ignora el mensaje. |
| **Reintentos con espera creciente** | Consumidor | 3–5 intentos con espera exponencial (1 s, 2 s, 4 s...). Solo para errores transitorios (BD caída, timeout). |
| **Cola de mensajes fallidos (DLQ)** | Cola | Tras agotar reintentos o ante error no recuperable (JSON inválido, regla violada) → DLQ. Se revisa y se reprocesa manualmente. |
| **Protección contra desorden** | Consumidor | Eventos instantánea: comparar `versionAgregado`. Si llega un `ViajeActualizado` antes que `ViajeProgramado`, se crea la proyección con lo que trae. |
| **Mensaje venenoso** | Consumidor | Nunca bloquear la cola: error no recuperable → DLQ de inmediato, sin reintentar. |

> **Sugerencia de implementación:** crear una librería compartida `eventos-comun` con el sobre, los *records* de cada payload, el publicador con outbox y la base del consumidor idempotente. Los 9 servicios la usan.

---

## 2. Eventos por productor

Prioridad: **P0** = flujos principales · **P1** = importante · **P2** = deseable.

### 2.1 Identidad y Administración (`servicio-identidad`)

| Evento | Tipo | Prio | Llave de ruteo | Consumidores |
|---|---|---|---|---|
| `UsuarioCreado` | Instantánea | P0 | `identidad.usuario.creado` | Clientes y Cartera, Notificaciones |
| `UsuarioActualizado` | Instantánea | P0 | `identidad.usuario.actualizado` | Clientes y Cartera, Notificaciones |
| `SucursalCreada` | Instantánea | P0 | `identidad.sucursal.creada` | Flota, Viajes, Alquileres, Costos, Reportes |
| `SucursalActualizada` | Instantánea | P0 | `identidad.sucursal.actualizada` | Flota, Viajes, Alquileres, Costos, Reportes |
| `EnlaceContrasenaEmitido` | Hecho | P1 | `identidad.usuario.enlace-contrasena-emitido` | Notificaciones |
| `ContrasenaCambiada` | Hecho | P1 | `identidad.usuario.contrasena-cambiada` | Notificaciones |

```json
// UsuarioCreado / UsuarioActualizado  (misma estructura)
{
  "idUsuario": "uuid",
  "correo": "string",
  "nombreCompleto": "string",
  "rol": "ADMIN_SISTEMA | ADMIN_SUCURSAL | CAJERO | CHOFER | CLIENTE",
  "idSucursal": "uuid?",          // obligatorio para ADMIN_SUCURSAL, CAJERO y CHOFER
  "activo": true
}
```

- La desactivación y el cambio de correo viajan como `UsuarioActualizado` con `activo=false` o el nuevo `correo`. No hay evento aparte.
- **Clientes y Cartera** actúa con **cualquier rol** (el enunciado dice que todos los usuarios tienen perfil y pueden ser clientes de cualquier sucursal): crea el perfil base y la cartera en saldo 0 (si no existen). Si `activo=false`, bloquea las operaciones de la cartera.
- **Notificaciones** hace *upsert* en su tabla local `contacto`.

```json
// SucursalCreada / SucursalActualizada  (misma estructura)
{
  "idSucursal": "uuid",
  "nombre": "string",
  "departamento": "string",
  "direccion": "string",
  "latitud": 14.8444,        // para el mapa de rutas (Leaflet)
  "longitud": -91.5198,
  "activa": true
}
```

```json
// EnlaceContrasenaEmitido
{
  "idUsuario": "uuid",
  "correo": "string",
  "nombreCompleto": "string",
  "proposito": "RESTABLECIMIENTO | ACTIVACION",
  "token": "string",                       // en claro: solo viaja en este evento y en el correo
  "expiraEn": "2026-10-06T15:30:00Z"
}

// ContrasenaCambiada
{
  "idUsuario": "uuid",
  "correo": "string",
  "nombreCompleto": "string",
  "motivo": "CAMBIO | RESTABLECIMIENTO | ACTIVACION",
  "cambiadaEn": "2026-10-06T15:12:00Z"
}
```

- Identidad **no envía correos**: publica estos eventos y Notificaciones los envía.
- Los dos llevan el `correo` en el payload, así Notificaciones no depende de que su copia local de contactos esté al día.
- `EnlaceContrasenaEmitido` contiene un secreto (el token): conexión `amqps://`, enlace de un solo uso y vigencia corta (30 min restablecimiento · 48 h activación), el *job* de limpieza del outbox borra las filas ya publicadas con más de 24 h, y **nunca se imprimen estos payloads en los logs**.
- Notificaciones registra `ENLACE_CONTRASENA` y `CONTRASENA_CAMBIADA` en su bitácora, pero para `ENLACE_CONTRASENA` **no guarda el cuerpo del correo** (texto fijo "[contenido sensible no almacenado]").
- `ACTIVACION`: al crear un usuario `ADMIN_SUCURSAL`, `CAJERO` o `CHOFER`, Identidad emite un enlace para que defina su propia contraseña.

### 2.2 Flota (`servicio-flota`)

| Evento | Tipo | Prio | Llave de ruteo | Consumidores |
|---|---|---|---|---|
| `BusRegistrado` | Instantánea | P0 | `flota.bus.registrado` | Viajes, Costos, Reportes |
| `BusActualizado` | Instantánea | P0 | `flota.bus.actualizado` | Viajes, Costos, Reportes |
| `ChoferRegistrado` | Instantánea | P0 | `flota.chofer.registrado` | Viajes, Notificaciones, Reportes |
| `ChoferActualizado` | Instantánea | P0 | `flota.chofer.actualizado` | Viajes, Notificaciones, Reportes |

```json
// BusRegistrado / BusActualizado
{
  "idBus": "uuid",
  "idSucursal": "uuid",
  "placa": "string",
  "marca": "string",
  "modelo": "string",
  "anioFabricacion": 2019,
  "capacidadPasajeros": 45,
  "estadoOperativo": "DISPONIBLE | EN_TRANSITO | EN_TALLER | FUERA_DE_SERVICIO",
  "activo": true
}
```

```json
// ChoferRegistrado / ChoferActualizado
{
  "idChofer": "uuid",
  "idUsuario": "uuid?",             // si el chofer tiene acceso al sistema
  "idSucursal": "uuid",
  "nombreCompleto": "string",
  "correo": "string",               // necesario para los avisos de licencia
  "telefono": "string",
  "numeroLicencia": "string",
  "tipoLicencia": "A | B | C | M | E",
  "fechaVencimientoLicencia": "2027-03-15",
  "salarioBaseViaje": "150.00",
  "activo": true
}
```

- **Notificaciones** programa los avisos a **30, 15 y 3 días** antes de `fechaVencimientoLicencia` al recibir cualquiera de los dos eventos (ver §4.5).
- **Flota no publica `BusActualizado` por cambios automáticos** de kilometraje ni del estado `EN_TRANSITO` ↔ `DISPONIBLE`. Actualiza ambos al consumir `SalidaRegistrada` / `LlegadaRegistrada`; Reportes deriva kilometraje y estado de esos mismos eventos. Así se evitan eventos en cadena. Los cambios que hace el administrador (por ejemplo, enviar un bus a `EN_TALLER`) sí publican `BusActualizado`, y Viajes los usa para no asignar buses en taller o fuera de servicio.

### 2.3 Rutas y Viajes (`servicio-viajes`)

| Evento | Tipo | Prio | Llave de ruteo | Consumidores |
|---|---|---|---|---|
| `RutaCreada` | Instantánea | P0 | `viajes.ruta.creada` | Reportes |
| `RutaActualizada` | Instantánea | P0 | `viajes.ruta.actualizada` | Reportes |
| `RutaEliminada` | Hecho | P0 | `viajes.ruta.eliminada` | Reportes |
| `ViajeProgramado` | Instantánea | P0 | `viajes.viaje.programado` | Boletos, Alquileres, Reportes |
| `ViajeActualizado` | Instantánea | P0 | `viajes.viaje.actualizado` | Boletos, Alquileres, Reportes |
| `ViajeEliminado` | Hecho | P0 | `viajes.viaje.eliminado` | Boletos, Alquileres, Reportes |
| `SalidaRegistrada` | Hecho | P0 | `viajes.viaje.salida-registrada` | Flota, Reportes |
| `LlegadaRegistrada` | Hecho | P0 | `viajes.viaje.llegada-registrada` | Flota, Costos, Alquileres, Reportes |

```json
// RutaCreada / RutaActualizada
{
  "idRuta": "uuid",
  "idSucursalOrigen": "uuid",
  "idSucursalDestino": "uuid",
  "distanciaKm": 205.5,
  "precioBoleto": "85.00"
}

// RutaEliminada
{ "idRuta": "uuid", "eliminadaEn": "2026-10-20T14:00:00Z" }
```

```json
// ViajeProgramado / ViajeActualizado  (instantánea completa del viaje)
{
  "idViaje": "uuid",
  "tipoViaje": "REGULAR | ALQUILER_PRIVADO",       // inmutable una vez creado
  "estado": "PROGRAMADO | PENDIENTE_ASIGNACION | EN_TRANSITO | FINALIZADO",
  "idRuta": "uuid?",                                // solo REGULAR
  "idSucursalOrigen": "uuid",
  "nombreOrigen": "string",                         // texto libre en alquileres
  "nombreDestino": "string",
  "idAlquiler": "uuid?",                            // solo ALQUILER_PRIVADO
  "fechaHoraSalida": "2026-10-25T06:00:00-06:00",
  "fechaHoraLlegadaEstimada": "2026-10-25T10:30:00-06:00",
  "precioBoleto": "85.00?",                         // solo REGULAR
  "bus": { "idBus": "uuid", "placa": "string", "capacidad": 45 },     // null si PENDIENTE_ASIGNACION
  "chofer": { "idChofer": "uuid", "nombreCompleto": "string" }        // null si PENDIENTE_ASIGNACION
}
```

- Un viaje de alquiler nace con `estado=PENDIENTE_ASIGNACION` y `bus/chofer = null`. Cuando el administrador asigna los recursos, Viajes publica `ViajeActualizado`.
- **Boletos** solo proyecta los viajes con `tipoViaje = REGULAR`. Rechaza la venta si `estado <> PROGRAMADO` o si la hora de salida ya pasó.
- **`ViajeActualizado` se publica** al asignar o cambiar recursos, al cambiar horarios y **también al registrar la salida (`EN_TRANSITO`) y la llegada (`FINALIZADO`)**, siempre con `versionAgregado` incrementada.
- **Alquileres** usa `idAlquiler` para vincular el viaje con su alquiler.

```json
// ViajeEliminado
{
  "idViaje": "uuid",
  "tipoViaje": "REGULAR | ALQUILER_PRIVADO",
  "idAlquiler": "uuid?",
  "eliminadoEn": "2026-10-20T14:00:00Z",
  "idUsuarioEliminador": "uuid"
}
```

> Solo se permite eliminar si el viaje **no ha iniciado ni finalizado** (validado en Viajes antes de publicar).

```json
// SalidaRegistrada  (registro inmutable)
{
  "idViaje": "uuid",
  "idBus": "uuid",
  "idChofer": "uuid",
  "fechaHoraSalidaReal": "2026-10-25T06:07:00-06:00",
  "kilometrajeInicial": 123450.0,
  "idUsuarioRegistro": "uuid"
}

// LlegadaRegistrada  (registro inmutable)
{
  "idViaje": "uuid",
  "tipoViaje": "REGULAR | ALQUILER_PRIVADO",
  "idAlquiler": "uuid?",
  "idRuta": "uuid?",
  "idSucursalOrigen": "uuid",
  "idBus": "uuid",
  "idSucursalBus": "uuid",
  "idChofer": "uuid",
  "salarioBaseChofer": "150.00",        // instantánea al momento de la llegada
  "fechaHoraSalidaReal": "2026-10-25T06:07:00-06:00",
  "fechaHoraLlegadaReal": "2026-10-25T10:41:00-06:00",
  "kilometrajeInicial": 123450.0,
  "kilometrajeFinal": 123655.5,
  "distanciaRecorridaKm": 205.5,        // kilometrajeFinal - kilometrajeInicial
  "gastoCombustibleTotal": "420.00",
  "idUsuarioRegistro": "uuid"
}
```

- `LlegadaRegistrada` lleva **todo lo que Costos necesita** (kilómetros, combustible, salario, tipo de viaje, sucursal del bus) para no consultar a Viajes.
- No existe `ViajeFinalizado`: `LlegadaRegistrada` es el hecho final. Alquileres lo usa para cerrar el alquiler si `idAlquiler != null`.

### 2.4 Boletos (`servicio-boletos`)

| Evento | Tipo | Prio | Llave de ruteo | Consumidores |
|---|---|---|---|---|
| `VentaBoletosRegistrada` | Hecho | P0 | `boletos.venta.registrada` | Notificaciones, Reportes |
| `BoletosAnulados` | Hecho | P0 | `boletos.venta.anulada` | Clientes y Cartera, Notificaciones, Reportes |

```json
// VentaBoletosRegistrada  (una compra = un evento, con varios asientos)
{
  "idCompra": "uuid",                  // idempotencia de la compra
  "idViaje": "uuid",
  "idRuta": "uuid",
  "canal": "EN_LINEA | MOSTRADOR",
  "asientos": [12, 13],
  "precioUnitario": "85.00",
  "montoTotal": "170.00",
  "idUsuarioComprador": "uuid?",       // null en venta presencial sin cuenta
  "nitComprador": "string",            // "CF" si es el cliente genérico del mostrador
  "nombreComprador": "string",
  "idPago": "uuid?",                   // pago en cartera; null en mostrador
  "fechaPago": "2026-10-20",           // elegida por el usuario
  "idSucursalIngreso": "uuid",         // sucursal a la que se acredita el ingreso
  "idUsuarioVendedor": "uuid?"         // cajero / administrador en mostrador
}
```

- **Notificaciones** envía confirmación por correo solo si `idUsuarioComprador != null` (su correo está en `contacto`).
- **Reportes** registra el ingreso en `idSucursalIngreso`.

```json
// BoletosAnulados  (uno por compra afectada, p. ej. al eliminarse un viaje)
{
  "idCompra": "uuid",
  "idViaje": "uuid",
  "motivo": "VIAJE_ELIMINADO",
  "canal": "EN_LINEA | MOSTRADOR",
  "montoTotal": "170.00",
  "idUsuarioComprador": "uuid?",
  "idPago": "uuid?",
  "idSucursalIngreso": "uuid"
}
```

- **Clientes y Cartera** reembolsa a la cartera **solo si `canal = EN_LINEA`**, usando `idCompra` como clave idempotente → publica `CarteraReembolsada`.
- **Reportes** revierte el ingreso.

### 2.5 Clientes y Cartera (`servicio-clientes`)

| Evento | Tipo | Prio | Llave de ruteo | Consumidores |
|---|---|---|---|---|
| `CarteraRecargada` | Hecho | P2 | `cartera.cartera.recargada` | Notificaciones |
| `CarteraReembolsada` | Hecho | P2 | `cartera.cartera.reembolsada` | Notificaciones |
| `PerfilClienteActualizado` | Instantánea | P0 | `cartera.cliente.actualizado` | Boletos, Alquileres |

```json
// CarteraRecargada
{
  "idMovimiento": "uuid",
  "idCartera": "uuid",
  "idUsuario": "uuid",
  "monto": "200.00",
  "saldoPosterior": "350.00",
  "fechaRecarga": "2026-10-20"          // elegida por el usuario
}

// CarteraReembolsada
{
  "idMovimiento": "uuid",
  "idCartera": "uuid",
  "idUsuario": "uuid",
  "monto": "170.00",
  "tipoReferencia": "BOLETOS | ALQUILER",
  "saldoPosterior": "520.00",
  "idReferencia": "uuid (idCompra o idAlquiler)",
  "motivo": "VIAJE_ELIMINADO"
}

// PerfilClienteActualizado  (instantánea del perfil comercial; misma estructura al crear y al actualizar)
{
  "idUsuario": "uuid",                   // = idAgregado
  "nit": "1234567K?",                    // normalizado; null mientras el perfil no tenga NIT
  "nombreCompleto": "string",
  "activo": true
}
```

- **`PerfilClienteActualizado`** se publica cuando Clientes y Cartera crea el cliente al consumir `UsuarioCreado` (con `nit = null`) y cada vez que cambian el nombre, el NIT o `activo`. `versionAgregado` = versión del cliente. Boletos y Alquileres hacen *upsert* en `cliente_ref` comparando la versión.
- Boletos y Alquileres usan `cliente_ref` para cobrar con el NIT y el nombre del usuario autenticado (nunca los toman del cuerpo de la petición) y para el historial por NIT. Sin NIT en el perfil, la compra o el alquiler en línea se rechaza con `422 PERFIL_INCOMPLETO`.
- `saldoPosterior` permite que Notificaciones muestre el saldo en el correo sin consultar a Cartera.
- El reembolso se aplica aunque el cliente esté inactivo, y su `fecha_movimiento` sale de `canceladoEn` (alquiler) u `ocurridoEn` (boletos), en hora de Guatemala.

> Una recarga **no es un ingreso** del negocio (es saldo a favor del cliente). El ingreso se reconoce al comprar el boleto o confirmar el alquiler. Por eso Reportes no consume estos eventos.

### 2.6 Alquileres Privados (`servicio-alquileres`)

| Evento | Tipo | Prio | Llave de ruteo | Consumidores |
|---|---|---|---|---|
| `AlquilerSolicitado` | Hecho | P1 | `alquileres.alquiler.solicitado` | Notificaciones |
| `AlquilerCotizado` | Hecho | P1 | `alquileres.alquiler.cotizado` | Notificaciones |
| `AlquilerConfirmado` | Hecho | P0 | `alquileres.alquiler.confirmado` | Viajes, Reportes, Notificaciones |
| `AlquilerCancelado` | Hecho | P0 | `alquileres.alquiler.cancelado` | Clientes y Cartera, Reportes, Notificaciones |

```json
// AlquilerSolicitado  → avisa al administrador de la sucursal de origen
{
  "idAlquiler": "uuid",
  "idUsuarioCliente": "uuid?",
  "nombreCliente": "string",
  "nit": "string",
  "idSucursalOrigen": "uuid",
  "origen": "string",
  "destino": "string",
  "fechaHoraSalida": "2026-11-10T07:00:00-06:00",
  "fechaHoraRetorno": "2026-11-12T18:00:00-06:00?",
  "numeroPasajeros": 30,
  "precioEstimado": "3500.00"           // distancia × precio por km × (2 si ida y vuelta) + días × precio por día
}

// AlquilerCotizado  → avisa al cliente que ya puede pagar
{
  "idAlquiler": "uuid",
  "idUsuarioCliente": "uuid?",
  "precioFinal": "3800.00",
  "fechaHoraLlegadaEstimada": "2026-11-12T20:00:00-06:00",   // la fija el administrador al cotizar
  "idUsuarioCotizador": "uuid"
}
```

```json
// AlquilerConfirmado  = contratación CONFIRMADA y PAGADA
{
  "idAlquiler": "uuid",
  "idUsuarioCliente": "uuid?",
  "nombreCliente": "string",
  "nit": "string",
  "idSucursalOrigen": "uuid",              // también es la sucursal de ingreso
  "origen": "string",
  "destino": "string",
  "fechaHoraSalida": "2026-11-10T07:00:00-06:00",
  "fechaHoraRetorno": "2026-11-12T18:00:00-06:00?",
  "fechaHoraLlegadaEstimada": "2026-11-12T20:00:00-06:00",   // fin del servicio (en ida y vuelta, regreso al origen)
  "idaYVuelta": true,
  "numeroPasajeros": 30,
  "precioTotal": "3800.00",
  "idPago": "uuid?",                       // null si se pagó en efectivo en mostrador
  "fechaPago": "2026-10-20"                // elegida por el usuario
}
```

- **Viajes** crea el viaje en `PENDIENTE_ASIGNACION` (idempotente por `idAlquiler`). **Un alquiler, sea de ida o de ida y vuelta, genera un solo viaje**: una salida y una llegada registradas, un kilometraje final, un gasto de combustible y un solo pago al chofer (con el 15 % adicional). El bus queda reservado durante todo el intervalo.
- **Reportes** registra el ingreso por alquiler.
- Debe contener todo lo necesario para crear el viaje **sin consultar a Alquileres**.

```json
// AlquilerCancelado  (p. ej. el administrador elimina el viaje de un alquiler ya pagado)
{
  "idAlquiler": "uuid",
  "idUsuarioCliente": "uuid?",
  "nombreCliente": "string",
  "nit": "string",
  "idSucursalOrigen": "uuid",
  "precioTotal": "3800.00",
  "idPago": "uuid?",                       // null si se pagó en mostrador
  "motivo": "VIAJE_ELIMINADO",
  "canceladoEn": "2026-10-22T09:15:00Z"
}
```

- **Clientes y Cartera** reembolsa **si `idPago != null`** (`tipoReferencia = ALQUILER`, idempotente por `idAlquiler`) y publica `CarteraReembolsada`.
- **Reportes** revierte el ingreso del alquiler. **Notificaciones** avisa al cliente.
- Por ahora la cancelación solo nace de la eliminación del viaje por el administrador. Una cancelación iniciada por el cliente no está definida en el enunciado.

### 2.7 Costos Operativos (`servicio-costos`)

| Evento | Tipo | Prio | Llave de ruteo | Consumidores |
|---|---|---|---|---|
| `GastoCombustibleRegistrado` | Hecho | P0 | `costos.combustible.registrado` | Reportes |
| `DepreciacionCalculada` | Hecho | P0 | `costos.depreciacion.calculada` | Reportes |
| `GastoTallerRegistrado` | Hecho | P0 | `costos.taller.registrado` | Reportes |
| `TarifaDepreciacionCambiada` | Hecho | P1 | `costos.depreciacion.tarifa-cambiada` | Reportes |
| `PagoChoferRegistrado` | Hecho | P0 | `costos.pago-chofer.registrado` | Reportes |

```json
// GastoCombustibleRegistrado  (generado al consumir LlegadaRegistrada)
{
  "idGasto": "uuid",
  "idViaje": "uuid",
  "idBus": "uuid",
  "idSucursal": "uuid",                  // idSucursalBus
  "monto": "420.00",
  "fechaGasto": "2026-10-25"
}

// DepreciacionCalculada
{
  "idDepreciacion": "uuid",
  "idViaje": "uuid",
  "idBus": "uuid",
  "idSucursal": "uuid",
  "distanciaKm": 205.5,
  "montoPorKm": "2.00",                  // tarifa vigente al calcular (se guarda, no se consulta luego)
  "monto": "411.00",
  "fechaCalculo": "2026-10-25"
}

// TarifaDepreciacionCambiada
{
  "montoPorKm": "2.50",
  "vigenteDesde": "2026-11-01",
  "idUsuarioCambio": "uuid"
}
```

```json
// GastoTallerRegistrado
{
  "idGasto": "uuid",
  "idBus": "uuid",
  "idSucursal": "uuid",
  "fechaServicio": "2026-10-18",         // elegida por el usuario
  "total": "1850.00",
  "lineas": [
    {
      "idLinea": "uuid",
      "idCategoria": "uuid",
      "nombreCategoria": "Llantas",      // instantánea del nombre
      "descripcion": "string",
      "tipo": "MANO_DE_OBRA | REPUESTO",
      "monto": "1500.00"
    }
  ]
}

// PagoChoferRegistrado  (el salario del chofer cuenta como gasto; se genera al consumir LlegadaRegistrada)
{
  "idPago": "uuid",
  "idViaje": "uuid",
  "idChofer": "uuid",
  "idSucursal": "uuid",
  "salarioBase": "150.00",
  "multiplicador": 1.15,                 // 1.00 REGULAR · 1.15 ALQUILER_PRIVADO
  "monto": "172.50",
  "fechaPago": "2026-10-25"
}
```

- Las categorías llevan `nombreCategoria` en cada línea. Así Reportes no necesita un evento de catálogo.
- Los gastos de taller son **inmutables** una vez registrados (igual que combustible y depreciación). El enunciado solo habla de registrar. Si el catedrático exige corregirlos, se agregaría `GastoTallerAnulado`.

---

## 3. Qué consume cada servicio

| Servicio | Consume | Para qué |
|---|---|---|
| **Clientes y Cartera** | `UsuarioCreado`, `UsuarioActualizado`, `BoletosAnulados`, `AlquilerCancelado` | Crear perfil y cartera; bloquear usuarios inactivos; reembolsar |
| **Notificaciones** | `UsuarioCreado`, `UsuarioActualizado`, `ChoferRegistrado`, `ChoferActualizado`, `VentaBoletosRegistrada`, `BoletosAnulados`, `CarteraRecargada`, `CarteraReembolsada`, `AlquilerSolicitado`, `AlquilerCotizado`, `AlquilerConfirmado`, `AlquilerCancelado`, `EnlaceContrasenaEmitido`, `ContrasenaCambiada` | Mantener contactos; programar avisos; enviar correos |
| **Flota** | `SucursalCreada`, `SucursalActualizada`, `SalidaRegistrada`, `LlegadaRegistrada` | Validar sucursales; actualizar kilometraje y estado del bus |
| **Viajes** | `SucursalCreada`, `SucursalActualizada`, `BusRegistrado`, `BusActualizado`, `ChoferRegistrado`, `ChoferActualizado`, `AlquilerConfirmado` | Proyecciones de sucursales, buses y choferes; crear viajes de alquiler |
| **Boletos** | `ViajeProgramado`, `ViajeActualizado`, `ViajeEliminado`, `PerfilClienteActualizado` | Proyección de viajes regulares; liberar asientos y anular boletos; NIT y nombre del comprador |
| **Alquileres** | `SucursalCreada`, `SucursalActualizada`, `ViajeProgramado`, `ViajeActualizado`, `ViajeEliminado`, `LlegadaRegistrada`, `PerfilClienteActualizado` | Vincular viaje con alquiler; mostrar bus asignado; cerrar alquiler; NIT y nombre del cliente |
| **Costos** | `SucursalCreada`, `SucursalActualizada`, `BusRegistrado`, `BusActualizado`, `LlegadaRegistrada` | Proyección de buses y sucursales; calcular combustible, depreciación y salario |
| **Reportes** | Casi todos (ver abajo) | Proyecciones de lectura |
| **Identidad** | — | No consume eventos |

**Reportes consume:** `SucursalCreada/Actualizada`, `BusRegistrado/Actualizado`, `ChoferRegistrado/Actualizado`, `RutaCreada/Actualizada/Eliminada`, `ViajeProgramado/Actualizado/Eliminado`, `SalidaRegistrada`, `LlegadaRegistrada`, `VentaBoletosRegistrada`, `BoletosAnulados`, `AlquilerConfirmado`, `AlquilerCancelado`, `GastoCombustibleRegistrado`, `DepreciacionCalculada`, `TarifaDepreciacionCambiada`, `GastoTallerRegistrado`, `PagoChoferRegistrado`.

El estado operativo del bus en los reportes se deriva de `SalidaRegistrada` (`EN_TRANSITO`) y `LlegadaRegistrada` (`DISPONIBLE`).

> **Cambio respecto a la versión anterior:** Alquileres y Costos ahora también consumen `Sucursal*`, porque guardan una copia local de las sucursales (el modelo de datos las usa para validar la sucursal de origen y para los reportes por sucursal).

---

## 4. Flujos que usan estos eventos

### 4.1 Compra de boletos en línea (saga con REST + eventos)

```
1. Boletos   reserva asientos     (BD local, índice único (id_viaje, numero_asiento) → estado RESERVADO)
2. Boletos   → Cartera (REST)     POST /cartera/pagos   [Clave-Idempotencia: idCompra]
3a. Pago OK    → Boletos confirma (CONFIRMADA) y publica VentaBoletosRegistrada
3b. Pago falla → Boletos libera los asientos (compensación); no se publica nada
4.  Notificaciones (correo) y Reportes (ingreso) consumen VentaBoletosRegistrada
```

- Si Boletos cae entre el paso 2 y el 3a, un *job* de recuperación revisa las reservas en `RESERVADA` con más de N minutos y consulta `GET /cartera/pagos?tipoReferencia=BOLETOS&idReferencia={idCompra}`: si hubo pago, confirma; si no, libera.
- La exclusividad del asiento la garantiza el **índice único parcial en BD**, no el broker.

### 4.2 Alquiler privado

```
1. Alquileres  AlquilerSolicitado → Notificaciones avisa al administrador de sucursal
2. El administrador confirma/modifica el precio → AlquilerCotizado → Notificaciones avisa al cliente
3. El cliente paga: Alquileres → Cartera (REST, idempotente)
4. Alquileres publica AlquilerConfirmado
5. Viajes crea el viaje PENDIENTE_ASIGNACION → publica ViajeProgramado
6. El administrador asigna bus y chofer en Viajes → ViajeActualizado (Alquileres y Reportes se actualizan)
7. Salida y llegada → SalidaRegistrada / LlegadaRegistrada
8. Alquileres cierra el alquiler con LlegadaRegistrada; Costos calcula los costos
```

- Si no se asigna bus ni chofer, el viaje permanece `PENDIENTE_ASIGNACION`. El administrador de sucursal ve esos alquileres en un listado de "pendientes de asignación" (sin evento adicional). Si decide no prestar el servicio, elimina el viaje (§4.3) y el alquiler se cancela con reembolso.
- Alquiler solicitado en mostrador sin cuenta: se paga en el mostrador (sin `idPago`), pero conserva `fechaPago` y NIT para el historial.

### 4.3 Eliminación de un viaje

```
1. Viajes valida (no iniciado ni finalizado), elimina y publica ViajeEliminado
2. Boletos: por cada compra del viaje libera asientos, marca los boletos ANULADO y publica BoletosAnulados
3. Cartera reembolsa las compras EN_LINEA (CarteraReembolsada); Reportes revierte los ingresos
4. Alquileres (si hay idAlquiler) pasa el alquiler a CANCELADO y publica AlquilerCancelado
5. Cartera reembolsa el pago (si hubo) y Reportes revierte el ingreso del alquiler
```

### 4.4 Llegada de un viaje

```
1. Viajes guarda LlegadaRegistrada (inmutable) vía outbox
2. Costos: GastoCombustibleRegistrado + DepreciacionCalculada (+ PagoChoferRegistrado)
3. Flota actualiza kilometraje · Alquileres cierra el alquiler · Reportes actualiza proyecciones
```

### 4.5 Avisos de vencimiento de licencia

```
1. Flota publica ChoferRegistrado / ChoferActualizado (con fechaVencimientoLicencia)
2. Notificaciones cancela los avisos pendientes de ese chofer y programa 3 nuevos (30, 15 y 3 días antes)
   - clave única (id_chofer, fecha_vencimiento, dias_anticipacion) → idempotente
   - se omiten los avisos cuyo día ya pasó
3. Al llegar cada fecha envía correo al chofer (correo del evento)
   y a los ADMIN_SUCURSAL activos de su sucursal (tabla contacto)
4. Si el chofer se desactiva (activo=false) se cancelan los avisos
```

---

## 5. Comunicación síncrona (REST entre microservicios)

| Origen → Destino | Operación | Por qué es síncrona |
|---|---|---|
| Boletos → Clientes y Cartera | `POST /cartera/pagos` (+ `GET /cartera/pagos` para consultar estado) | Hay que saber si se pagó antes de confirmar el boleto |
| Alquileres → Clientes y Cartera | `POST /cartera/pagos` | Igual: el alquiler solo queda efectuado si el pago se confirma |
| Flota → Viajes | `GET /viajes/activos?idBus=` o `?idChofer=` | Regla: no desactivar un bus ni un chofer con viajes programados o en tránsito |
| Viajes → Boletos | `GET /boletos/viajes/{idViaje}/asiento-maximo` | Regla: no cambiar a un bus con capacidad menor al mayor asiento ya vendido. Si Boletos no responde, se rechaza el cambio (falla cerrada) |

Todas las llamadas REST llevan el encabezado `Clave-Idempotencia`, tiempo límite (*timeout*) y reintento acotado (Resilience4j: retry + circuit breaker).

#### Contrato de `POST /cartera/pagos` (lo usan Boletos y Alquileres)

| Elemento | Detalle |
|---|---|
| Autenticación | `POST` exige el **JWT del usuario** reenviado (de ahí sale el cliente). `GET` también acepta el encabezado `X-Clave-Interna` (secreto compartido por variable de entorno, solo válido en rutas internas), porque los *jobs* de recuperación corren sin usuario. Esas rutas no se publican en el API Gateway |
| Encabezados | `Clave-Idempotencia` (idCompra o idAlquiler) |
| Cuerpo | `{ tipoReferencia: "BOLETOS" \| "ALQUILER", idReferencia, monto, fechaPago, descripcion }` |
| Respuesta | `201 { idPago, saldoPosterior }`. Si el pago ya existía: `200` con el mismo `idPago` |
| Errores | `422 SALDO_INSUFICIENTE` · `422 PERFIL_INCOMPLETO` (sin NIT) · `403 CUENTA_INACTIVA` |
| Consulta | `GET /cartera/pagos?tipoReferencia=&idReferencia=` → `200 { idPago, monto, fechaPago }` o `404`. La usan los *jobs* de recuperación de Boletos y Alquileres (con `X-Clave-Interna`) |

### Proporción síncrono / asíncrono (pares productor → consumidor)

| Estilo | Pares | Detalle |
|---|---|---|
| **Asíncrono (eventos)** | **27** | Identidad→{Cartera, Notificaciones, Flota, Viajes, Alquileres, Costos, Reportes} · Flota→{Viajes, Reportes, Notificaciones, Costos} · Viajes→{Reportes, Boletos, Alquileres, Flota, Costos} · Boletos→{Notificaciones, Reportes, Cartera} · Cartera→{Notificaciones, Boletos, Alquileres} · Alquileres→{Notificaciones, Viajes, Reportes, Cartera} · Costos→{Reportes} |
| **Síncrono (REST)** | **4** | Boletos→Cartera · Alquileres→Cartera · Flota→Viajes · Viajes→Boletos |

→ **27 de 31 interacciones (≈ 87 %) son por eventos.** Se cumple el 50 % que pide el enunciado con cualquier criterio de conteo razonable (por pares, por tipos de evento o por flujos). El criterio exacto conviene confirmarlo con el catedrático.

**32 tipos de evento** en total: Identidad 6 · Flota 4 · Viajes 8 · Boletos 2 · Cartera 3 · Alquileres 4 · Costos 5.

> Son tres métricas distintas y conviene no mezclarlas en la documentación: **32 tipos de evento** (contratos), **27 pares productor→consumidor asíncronos** y **4 pares síncronos**. El ≈ 87 % cuenta pares, no volumen de tráfico ni de procesamiento.

---

## 6. Decisiones

| # | Tema | Decisión | Nota |
|---|---|---|---|
| 1 | **Contactos en Notificaciones** |  **Decidido:** copia local (`contacto`) alimentada por `UsuarioCreado/Actualizado` | Necesaria para avisar al **administrador de sucursal** (el evento del chofer no conoce su correo). |
| 2 | **Salario del chofer como costo** |  **Decidido:** cuenta como gasto. `PagoChoferRegistrado` (P0), 15 % extra en alquiler; entra en gastos y ganancias | Documentarlo como supuesto en el manual técnico. |
| 3 | **Choferes con acceso al sistema** |  **Decidido:** el chofer tiene usuario con rol `CHOFER` solo para registrar su salida (y llegada); enlazado por `idUsuario` | Solo puede operar sobre sus propios viajes (validar que el viaje sea suyo). |
| 4 | **Reembolso al eliminar un viaje** |  **Decidido:** compras `EN_LINEA` se reembolsan a la cartera; compras `MOSTRADOR` solo revierten el ingreso; alquiler pagado con cartera se cancela (`AlquilerCancelado`) y se reembolsa | El enunciado no lo define: documentarlo como supuesto. |
| 5 | **Alquiler ida y vuelta** |  **Decidido:** un solo viaje con `fechaHoraLlegadaEstimada` = regreso al origen. El enunciado habla de un único registro de llegada, kilometraje final y combustible por alquiler | Documentarlo como supuesto. |
| 6 | **Eliminación en Boletos** |  **Decidido:** liberar asientos y marcar boletos `ANULADO` | Documentarlo como supuesto: conserva la auditoría. |
| 7 | **Cambio de bus en viaje con boletos vendidos** |  **Decidido:** Viajes consulta de forma síncrona a Boletos (`asiento-maximo`) y rechaza un bus con capacidad menor | Cuarto par síncrono; falla cerrada si Boletos no responde. |
| 8 | **Broker** |  **Decidido:** RabbitMQ (CloudAMQP en producción) | Kafka es válido, pero exige más infraestructura. |
| 9 | **Fecha de corte en reportes** |  **Decidido:** Reportes usa `fechaPago`, `fechaServicio`, `fechaGasto` (fechas de negocio) | Los filtros por intervalo no usan `ocurridoEn`. |
| 10 | **NIT del cliente en Boletos y Alquileres** |  **Decidido:** evento `PerfilClienteActualizado` + tabla `cliente_ref`; NIT normalizado con `Nit.normalizar()`; `CF` solo en mostrador | Evita una llamada síncrona y mantiene el 87 % de eventos. |
| 11 | **Precio estimado del alquiler** |  **Decidido:** `distancia × precio por km × (2 si ida y vuelta) + días × precio por día`; tarifas en `tarifa_alquiler`, configuradas por el administrador de sistema | El enunciado solo dice "el sistema calcula un precio estimado": documentarlo como supuesto. El administrador de sucursal lo confirma o lo modifica. |
| 12 | **Chofer ↔ usuario** |  **Decidido:** sin validación adicional contra Identidad. Flota guarda `id_usuario` al registrar al chofer y Viajes lo recibe en `chofer_ref` | Un `id_usuario` equivocado solo afecta la autorización de ese chofer. |
| 13 | **Desactivar un chofer** |  **Decidido:** solo si no tiene viajes programados o en tránsito (`GET /viajes/activos?idChofer=`) | El enunciado lo exige solo para buses; se aplica también a choferes por coherencia. |
