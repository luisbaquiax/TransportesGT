# servicio-alquileres (PrivateRentalService)

Alquileres de buses privados para TransportesGT: solicitud en línea o en mostrador, cotización por
sucursal, pago en línea (via Cartera, saga fail-closed) o en efectivo, y ciclo de vida
`SOLICITADO → COTIZADO → CONFIRMADO → FINALIZADO / CANCELADO`.

| | |
|---|---|
| Puerto | `8086` |
| Ruta base | `/v1/rentals` (`server.servlet.context-path`) |
| Swagger | <http://localhost:8086/v1/rentals/swagger-ui.html> |
| OpenAPI | <http://localhost:8086/v1/rentals/v3/api-docs> |
| Health | <http://localhost:8086/v1/rentals/actuator/health> |
| Base de datos | `bd_alquileres` (PostgreSQL), migraciones Flyway en `src/main/resources/db/migration` |

## Ejecutar en local

```bash
# 1. Infraestructura (PostgreSQL + RabbitMQ)
cd ../infra && docker compose up -d

# 2. Variables (archivo .env en esta carpeta; ver variables de entorno)
# 3. Arrancar (perfil dev por defecto)
./mvnw spring-boot:run
```

### Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | sí | Conexión a `bd_alquileres` |
| `RABBITMQ_URL` | sí | `amqp://usuario:clave@host:5672` (en la nube, `amqps://`) |
| `JWT_SECRET` | sí | Secreto HS256 en **Base64** (mínimo 32 bytes decodificados), el mismo del gateway |
| `CARTERA_URL` | no | URL base de Cartera (`http://localhost:8085/v1/customers`) |
| `ALQUILERES_FACTOR_CARRETERA` | no | Factor de seguridad sobre distancia recta (por defecto `1.3`) |
| `SERVER_PORT` | no | Por defecto `8086` |
| `SPRING_PROFILES_ACTIVE` | no | `dev` (por defecto) o `prod` |

## Datos de prueba (`V2__datos_prueba.sql`)

Sembrados al arrancar (idempotentes con `ON CONFLICT DO NOTHING`):

- 3 sucursales (Quetzaltenango, Ciudad de Guatemala, Huehuetenango) — las mismas de Identidad/Flota.
- 2 clientes de referencia para pruebas manuales.

**Los datos iniciales no generan eventos.** Después de levantar los demás servicios, ejecute (con token de
ADMIN_SISTEMA) `POST /v1/rentals/alquileres/admin/republicar-eventos`: encola `AlquilerSolicitado` de todo
lo existente para poblar proyecciones de Costos. Es idempotente.

## Endpoints

Todos requieren JWT salvo la lectura pública. `ADMIN_SUCURSAL` solo opera los recursos de su propia
sucursal; `ADMIN_SISTEMA` puede consultar todo.

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| POST | `/alquileres/solicitudes` | autenticado | Solicitud en línea; calcula precio con tarifa vigente y publica `AlquilerSolicitado` |
| POST | `/alquileres/mostrador` | SUC, CAJ | Solicitud presencial (NIT y nombre del cuerpo, canal MOSTRADOR) |
| POST | `/alquileres/{id}/cotizacion` | SUC | Fija precio final y llegada estimada. `SOLICITADO/COTIZADO → COTIZADO`. Publica `AlquilerCotizado` |
| POST | `/alquileres/{id}/pago` | dueño en línea | Saga con Cartera (`Clave-Idempotencia=idAlquiler`); si Cartera no responde, 503 sin confirmar. Publica `AlquilerConfirmado` |
| POST | `/alquileres/{id}/pago-mostrador` | SUC, CAJ | Pago en efectivo; sin cartera. Publica `AlquilerConfirmado` |
| GET | `/alquileres/mios` | autenticado | Historial del usuario (en línea + mostrador con su NIT) |
| GET | `/alquileres` | SUC, CAJ, SIS | Lista paginada; filtros `estado`, `desde`, `hasta`, `sinAsignar` |
| GET | `/alquileres/{id}` | autenticado | Detalle con historial de cotizaciones |
| POST | `/alquileres/admin/republicar-eventos` | SIS | Encola eventos de todo lo existente |

Códigos de error estables: `NO_AUTENTICADO` (401), `OPERACION_NO_AUTORIZADA` (403),
`ALQUILER_NO_ENCONTRADO` / `RUTA_NO_ENCONTRADA` (404), `TRANSICION_INVALIDA` (409),
`SOLICITUD_INVALIDA` (400), `PERFIL_INCOMPLETO` / `SIN_TARIFA` (422), `SERVICIO_NO_DISPONIBLE` (503).

## Colección Postman

[`postman/alquileres.postman_collection.json`](postman/alquileres.postman_collection.json) — use
`{{baseUrl}}` (`http://localhost:8086/v1/rentals`) y copie el token de login (colección Identidad) a
`{{token}}`.

## Pruebas

```bash
./mvnw verify        # unitarias + integración (Testcontainers: PostgreSQL + RabbitMQ)
```

- **75 pruebas** (48 unitarias + 27 integración), todas en verde.
- Cobertura jacoco bundle **93.0%** de líneas (mínimo exigido: 85%).
- Las integración usan contenedores reales: outbox, routing de RabbitMQ y triggers se prueban de verdad
  (no H2).

## Eventos

**Publica** (outbox → `transporte.eventos`): `AlquilerSolicitado`, `AlquilerCotizado`,
`AlquilerConfirmado`, `AlquilerCancelado`, `AlquilerFinalizado`. **Consume**: `SucursalCreada` /
`SucursalActualizada` (proyecta `sucursal_ref`, idempotente por `versionAgregado`), `ViajeProgramado`
(enlaza `id_viaje`), `ViajeActualizado` (enlaza si faltaba), `ViajeEliminado` (cancela el alquiler,
motivo `VIAJE_ELIMINADO`), `LlegadaRegistrada` (→ FINALIZADO). Los mensajes inválidos van a la DLQ.

```mermaid
flowchart LR
    subgraph Publica
        TX[(Transacción)] --> OUTBOX[outbox_evento]
        OUTBOX -->|publicador cada 1s| EX[transporte.eventos]
    end
    subgraph Consume
        EX --> Q1[alquileres-identidad.q] --> H1[ManejadorSucursalCreada/Actualizada]
        EX --> Q2[alquileres-viajes.q] --> H2[ManejadorViajeProgramado/Actualizado/Eliminado]
        H2 --> H3[ManejadorLlegadaRegistrada]
    end
    Q1 --> PROC[(evento_procesado)]
    Q2 --> PROC
    PAGO[Cartera /v1/customers/cartera/pagos] -.->|saga fail-closed| TX
```

## Modelo de datos

| Tabla | Descripción |
|---|---|
| `alquiler` | Aggregate root: canal, estado, cliente (nombre + NIT), origen/destino con coordenadas, distancia, precios, pago, viaje/bus/chofer, version (concurrencia optimista) |
| `cotizacion_alquiler` | Historial de cotizaciones del alquiler (precio, tipo, comentario) |
| `tarifa_alquiler` | Tarifa vigente por fecha (`precio_por_km`, `precio_por_dia`) |
| `sucursal_ref` | Proyección de Identidad (id, nombre, activa, version) |
| `cliente_ref` | Proyección de Identidad (id_usuario, NIT, nombre, activo) |
| `outbox_evento` | Outbox transaccional para publicar en RabbitMQ |
| `evento_procesado` | Deduplicación de eventos entrantes (idempotencia) |

### Precio

`precio = distancia_km × precioPorKm × (2 si idaYVuelta) + días × precioPorDia`

La distancia usa Haversine entre coordenadas multiplicada por `alquileres.factor-carretera` (1.3).
Ejemplo: Quetzaltenango → Ciudad de Guatemala ≈ 143 km con factor, ≈ 110 km recta.
