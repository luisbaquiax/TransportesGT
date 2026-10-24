# servicio-flota (FleetService)

Gestión de la flota de TransportesGT: buses y choferes por sucursal, fotos, kilometraje y estado
operativo. Publica sus eventos por **outbox transaccional** (librería `EventosComunesTrasnportesGt`) en el
exchange `transporte.eventos` y consume los eventos de Identidad (sucursales) y Viajes (salidas/llegadas).

| | |
|---|---|
| Puerto | `8082` |
| Ruta base | `/v1/fleet` (`server.servlet.context-path`; el gateway reenvía `/v1/fleet/**` sin reescribir) |
| Swagger | <http://localhost:8082/v1/fleet/swagger-ui.html> |
| OpenAPI | <http://localhost:8082/v1/fleet/v3/api-docs> |
| Health | <http://localhost:8082/v1/fleet/actuator/health> |
| Base de datos | `bd_flota` (PostgreSQL), migraciones Flyway en `src/main/resources/db/migration` |

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
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | sí | Conexión a `bd_flota` |
| `RABBITMQ_URL` | sí | `amqp://usuario:clave@host:5672` (en la nube, `amqps://`) |
| `JWT_SECRET` | sí | Secreto HS256 en **Base64** (mínimo 32 bytes decodificados), el mismo del gateway |
| `VIAJES_URL` | no | URL base de TripService (`http://localhost:8083/v1/trips`) |
| `FOTOS_DIRECTORIO` | no | Carpeta local de fotos (por defecto `./public/fotos`) |
| `FOTOS_URL_PUBLICA` | no | Prefijo público de las fotos |
| `FOTOS_NUBE_HABILITADA` / `FOTOS_NUBE_BUCKET` | no | Almacenamiento en Google Cloud Storage (por defecto local) |
| `SERVER_PORT` | no | Por defecto `8082` |
| `SPRING_PROFILES_ACTIVE` | no | `dev` (por defecto) o `prod` |

## Datos de prueba (`V2__datos_prueba.sql`)

Sembrados al arrancar (idempotentes con `ON CONFLICT DO NOTHING`):

- 3 sucursales (Quetzaltenango, Ciudad de Guatemala, Huehuetenango) — las mismas de Identidad.
- 9 buses (placas `P900ABC`–`P908ABC`): `P905ABC` inactivo, `P907ABC` en taller.
- 9 choferes (licencias `LIC-GT-101`–`LIC-GT-109`): `LIC-GT-101` vence pronto, `LIC-GT-109` inactivo.
- `LIC-GT-101`, `LIC-GT-104` y `LIC-GT-107` enlazados a los usuarios CHOFER de Identidad.

Los datos de todos los servicios están sincronizados desde sus migraciones `V2` (mismos IDs, versión 0; ver
`docs/datos-semilla.md`), así que no hace falta republicar nada al arrancar.
`POST /v1/fleet/admin/republicar-eventos` (SIS) sigue disponible para recuperar una proyección: encola `BusRegistrado` y
`ChoferRegistrado` de todo lo existente. Es idempotente.

## Endpoints

Todos requieren JWT salvo la lectura de fotos. `ADMIN_SUCURSAL` solo puede gestionar los recursos de su
propia sucursal; `ADMIN_SISTEMA` puede consultar todo.

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/buses` | SIS, SUC | Lista paginada; filtros `idSucursal`, `estadoOperativo`, `activo`, `texto` |
| GET | `/buses/{id}` | SIS, SUC | Detalle |
| POST | `/buses` | SUC | Crea bus (placa única, insensible a mayúsculas) |
| PUT | `/buses/{id}` | SUC | Actualiza (con `version` para concurrencia optimista) |
| PATCH | `/buses/{id}/activo` | SUC | Activa/desactiva; al desactivar consulta Viajes (fail-closed: 503 si Viajes no responde) |
| PATCH | `/buses/{id}/estado-operativo` | SUC | DISPONIBLE / EN_TALLER / FUERA_DE_SERVICIO (EN_TRANSITO solo vía eventos) |
| POST | `/buses/{id}/foto` | SUC | Sube foto (multipart `archivo`, JPEG/PNG/WebP, máx. 5 MB) |
| GET | `/choferes` | SIS, SUC | Lista paginada; filtros `idSucursal`, `activo`, `texto` |
| GET | `/choferes/{id}` | SIS, SUC | Detalle |
| POST | `/choferes` | SUC | Crea chofer (licencia única; `idUsuario` opcional de Identidad) |
| PUT | `/choferes/{id}` | SUC | Actualiza (con `version`) |
| PATCH | `/choferes/{id}/activo` | SUC | Activa/desactiva |
| POST | `/choferes/{id}/foto` | SUC | Sube foto |
| GET | `/fotos/{tipo}/{nombre}` | público | Lee la imagen (`buses` o `choferes`); el frontend la sirve en `<img src>` sin token |
| POST | `/admin/republicar-eventos` | SIS | Encola los eventos de todo lo existente |

Códigos de error estables: `NO_AUTENTICADO` (401), `ACCESO_DENEGADO` / `OPERACION_NO_AUTORIZADA` (403),
`BUS_NO_ENCONTRADO` / `CHOFER_NO_ENCONTRADO` / `RUTA_NO_ENCONTRADA` (404), `PLACA_DUPLICADA` /
`LICENCIA_DUPLICADA` / `USUARIO_YA_ENLAZADO` / `TRANSICION_INVALIDA` / `REGISTRO_MODIFICADO` (409),
`FOTO_INVALIDA` / `SOLICITUD_INVALIDA` (400), `BUS_CON_VIAJES` (409), `SERVICIO_NO_DISPONIBLE` (503).

## Colección Postman

[`postman/flota.postman_collection.json`](postman/flota.postman_collection.json) — use `{{baseUrl}}`
(`http://localhost:8082/v1/fleet`) y copie el token de login (colección Identidad) a `{{token}}`.

## Pruebas

```bash
./mvnw verify        # unitarias + integración (Testcontainers: PostgreSQL + RabbitMQ)
```

- **127 pruebas** (92 unitarias + 36 integración), todas en verde.
- Cobertura jacoco bundle **91.5%** de líneas (mínimo exigido: 85%, excluyendo config/dto/models/mappers).
- Las integración usan contenedores reales: los triggers de PostgreSQL y el outbox se prueban de verdad
  (no H2).

## Eventos

**Publica** (outbox → `transporte.eventos`): `BusRegistrado`, `BusActualizado`, `ChoferRegistrado`,
`ChoferActualizado`. **Consume**: `SucursalCreada` / `SucursalActualizada` (proyecta `sucursal_ref`,
idempotente por `versionAgregado`), `SalidaRegistrada` (bus → EN_TRANSITO, kilometraje monotónico),
`LlegadaRegistrada` (bus → DISPONIBLE). Los mensajes inválidos van a la DLQ.

```mermaid
flowchart LR
    subgraph Publica
        TX[(Transacción)] --> OUTBOX[outbox_evento]
        OUTBOX -->|publicador cada 1s| EX[transporte.eventos]
    end
    subgraph Consume
        EX --> Q1[flota-identidad.q] --> H1[ManejadorSucursalCreada/Actualizada]
        EX --> Q2[flota-viajes.q] --> H2[ManejadorSalidaRegistrada]
        H2 --> Q2 --> H3[ManejadorLlegadaRegistrada]
    end
    Q1 --> PROC[(evento_procesado)]
    Q2 --> PROC
```

## Modelo de datos

```mermaid
erDiagram
    sucursal_ref ||--o{ bus : "id_sucursal"
    sucursal_ref ||--o{ chofer : "id_sucursal"
    bus ||--o| outbox_evento : "id_agregado (bus)"
    chofer ||--o| outbox_evento : "id_agregado (chofer)"
    outbox_evento ||--o| evento_procesado : "id_evento (al consumir)"

    sucursal_ref {
        uuid id PK
        varchar nombre
        boolean activa
        bigint version
    }
    bus {
        uuid id PK
        uuid id_sucursal FK
        varchar placa UK "uq_bus_placa upper(placa)"
        varchar marca
        varchar modelo
        smallint anio_fabricacion
        smallint capacidad_pasajeros
        varchar estado_operativo "DISPONIBLE|EN_TRANSITO|EN_TALLER|FUERA_DE_SERVICIO"
        numeric kilometraje_actual "CHECK >= 0, monótono"
        varchar foto_url
        boolean activo
        bigint version
    }
    chofer {
        uuid id PK
        uuid id_usuario UK "usuario de Identidad (opcional)"
        uuid id_sucursal FK
        varchar nombre_completo
        varchar correo
        varchar telefono
        varchar numero_licencia UK
        varchar tipo_licencia "A|B|C|M|E"
        date fecha_vencimiento_licencia
        numeric salario_base_viaje
        varchar foto_url
        boolean activo
        bigint version
    }
    outbox_evento {
        uuid id PK
        varchar tipo_agregado "bus|chofer"
        uuid id_agregado
        varchar tipo_evento
        varchar llave_ruteo
        bigint version_agregado
        jsonb datos
        timestamptz publicado_en "NULL = pendiente"
    }
    evento_procesado {
        uuid id_evento PK
        varchar consumidor PK
        timestamptz procesado_en
    }
```

`bus` y `chofer` tienen el trigger `BEFORE DELETE` (`fn_bloquear_eliminacion`) que impide el borrado
físico: los registros se desactivan, no se eliminan.

## Arquitectura

```mermaid
flowchart TB
    subgraph FleetService[:8082 /v1/fleet]
        CTRL[Controllers<br/>Bus · Chofer · Fotos · Admin]
        SVC[Services<br/>BusServiceImpl · ChoferServiceImpl<br/>FotoServiceImpl · RepublicacionEventos]
        AUTH[AutorizacionSucursal]
        REPO[Repositories JPA]
        MAP[MapStruct Mappers]
        CLI[ViajesClient]
        EVT[EventosFlota + Manejadores]
        ALM[AlmacenamientoFotos<br/>Local | Nube GCS]
        CTRL --> SVC
        SVC --> AUTH
        SVC --> REPO
        SVC --> MAP
        SVC --> CLI
        SVC --> EVT
        SVC --> ALM
    end
    PG[(PostgreSQL<br/>bd_flota)]
    RM[(RabbitMQ<br/>transporte.eventos)]
    VIA[TripService :8083]
    ID[IdentityService :8081]
    GS[(Google Cloud Storage)]

    REPO <--> PG
    EVT <--> RM
    EVT -.->|SucursalCreada| RM
    CLI -->|GET viajes/activos| VIA
    ALM -.->|FOTOS_NUBE_HABILITADA| GS
    CTRL -.->|JWT validado| ID
```

Capas: `controllers` → `services` (con `AutorizacionSucursal` para la regla de sucursal y
`ConsultaViajes` como abstracción de TripService, DIP) → `repositories`/`mappers`. Las fotos se guardan en
disco local o GCS según `flota.fotos.nube.habilitada`.

## Despliegue (Cloud Run)

```mermaid
flowchart LR
    DEV[Desarrollador] -->|gcloud builds submit| AR[Artifact Registry<br/> imagen fleet-service]
    AR --> CR[Cloud Run<br/>servicio-flota]
    CR -->|SQL| CLOUDSQL[(Cloud SQL<br/>bd_flota)]
    CR -->|amqps| PUBSUB[RabbitMQ gestionado]
    CR -->|GCS| BUCKET[(Bucket fotos)]
    GW[Cloud Endpoints / Gateway] -->|/v1/fleet/**| CR
    CR -->|GET viajes/activos| TRIP[TripService en Cloud Run]
```

```bash
# Build y despliegue (requiere credenciales de GCP)
gcloud builds submit --tag REGION-docker.pkg.dev/PROYECTO/servicios/fleet-service .
gcloud run deploy servicio-flota \
  --image REGION-docker.pkg.dev/PROYECTO/servicios/fleet-service \
  --region REGION \
  --add-cloudsql-instances PROYECTO:REGION:instancia \
  --set-env-vars "SPRING_DATASOURCE_URL=jdbc:postgresql://.../bd_flota,VIAJES_URL=https://servicio-viajes-....run.app/v1/trips,FOTOS_NUBE_HABILITADA=true,FOTOS_NUBE_BUCKET=BUCKET,SPRING_PROFILES_ACTIVE=prod"
```

> El despliegue real de Cloud Run requiere credenciales de GCP (proyecto, región, Artifact Registry y
> Cloud SQL) que no están configuradas en este repositorio.
