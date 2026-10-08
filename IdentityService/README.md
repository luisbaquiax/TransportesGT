# servicio-identidad (IdentityService)

Identidad y Administración de TransportesGT: usuarios, roles, sucursales, emisión del JWT y gestión de contraseñas.
Publica sus eventos por **outbox transaccional** (librería `EventosComunesTrasnportesGt`) en el exchange
`transporte.eventos`. No consume eventos.

| | |
|---|---|
| Puerto | `8081` |
| Ruta base | `/v1/identity` (`server.servlet.context-path`; el gateway reenvía `/v1/identity/**` sin reescribir) |
| Swagger | <http://localhost:8081/v1/identity/swagger-ui.html> |
| OpenAPI | <http://localhost:8081/v1/identity/v3/api-docs> |
| Health | <http://localhost:8081/v1/identity/actuator/health> |
| Base de datos | `bd_identidad` (PostgreSQL), migraciones Flyway en `src/main/resources/db/migration` |

## Ejecutar en local

```bash
# 1. Infraestructura (PostgreSQL + RabbitMQ)
cd ../infra && docker compose up -d

# 2. Variables (archivo .env en esta carpeta; ver .env.example)
# 3. Arrancar (perfil dev por defecto)
./mvnw spring-boot:run
```

### Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | sí | Conexión a `bd_identidad` |
| `RABBITMQ_URL` | sí | `amqp://usuario:clave@host:5672` (en la nube, `amqps://`) |
| `JWT_SECRET` | sí | Secreto HS256 en **Base64** (mínimo 32 bytes decodificados), el mismo del gateway |
| `JWT_EXPIRATION` | no | Vigencia del token (`30m` a `60m`; por defecto `1h`). La app no arranca fuera de ese rango |
| `SERVER_PORT` | no | Por defecto `8081` |
| `SPRING_PROFILES_ACTIVE` | no | `dev` (por defecto) o `prod` |
| `IDENTIDAD_VIGENCIA_RESTABLECIMIENTO` / `IDENTIDAD_VIGENCIA_ACTIVACION` | no | `30m` y `48h` |

> `JWT_EXPIRATION_MS` del `.env` actual ya no se usa.

## Usuarios de prueba (`V2__datos_iniciales.sql`)

| Rol | Correo | Contraseña | Sucursal |
|---|---|---|---|
| ADMIN_SISTEMA | `admin@transportes.gt` | `Admin1234` | — |
| ADMIN_SUCURSAL | `admin.xela@transportes.gt` | `Admin1234` | Quetzaltenango |
| ADMIN_SUCURSAL | `admin.capital@transportes.gt` | `Admin1234` | Ciudad de Guatemala |
| ADMIN_SUCURSAL | `admin.huehue@transportes.gt` | `Admin1234` | Huehuetenango |
| CAJERO | `cajero.xela@transportes.gt` | `Cajero1234` | Quetzaltenango |
| CLIENTE | `cliente@transportes.gt` | `Cliente1234` | — |

Sucursales (coordenadas del parque central): Quetzaltenango `14.8347, -91.5181` · Ciudad de Guatemala
`14.6427, -90.5133` · Huehuetenango `15.3197, -91.4709`.

**Los datos iniciales no generan eventos.** Después de levantar los demás servicios, ejecute (con token de
ADMIN_SISTEMA) `POST /v1/identity/admin/republicar-eventos`: publica `SucursalCreada` y `UsuarioCreado` de todo lo
existente. Es idempotente, porque los consumidores comparan `versionAgregado`.

## Endpoints

| Método y ruta | Quién | Eventos |
|---|---|---|
| `POST /auth/login` | público | — |
| `POST /auth/registro` | público (crea CLIENTE) | UsuarioCreado |
| `GET /auth/yo` | autenticado | — |
| `POST /auth/cambiar-contrasena` | autenticado | ContrasenaCambiada(CAMBIO) |
| `POST /auth/olvide-contrasena` | público, siempre 202, máx. 3/hora | EnlaceContrasenaEmitido(RESTABLECIMIENTO) |
| `GET /auth/enlace-contrasena/validar?token=` | público (200 o 410) | — |
| `POST /auth/restablecer-contrasena` | público (RESTABLECIMIENTO y ACTIVACION) | ContrasenaCambiada |
| `POST /sucursales` | ADMIN_SISTEMA (sucursal + primer ADMIN_SUCURSAL) | SucursalCreada, UsuarioCreado, EnlaceContrasenaEmitido(ACTIVACION) |
| `GET /sucursales`, `GET /sucursales/{id}` | autenticado | — |
| `PUT /sucursales/{id}`, `PATCH .../activar`, `PATCH .../desactivar` | ADMIN_SISTEMA | SucursalActualizada |
| `GET /sucursales/{id}/administradores` | ADMIN_SISTEMA | — |
| `POST /usuarios` | ADMIN_SISTEMA → ADMIN_SISTEMA/ADMIN_SUCURSAL · ADMIN_SUCURSAL → CAJERO/CHOFER de su sucursal | UsuarioCreado, EnlaceContrasenaEmitido(ACTIVACION) |
| `GET /usuarios` | ADMIN_SISTEMA (todos) · ADMIN_SUCURSAL (su sucursal) | — |
| `GET /usuarios/{id}` | ADMIN_SISTEMA · ADMIN_SUCURSAL (su sucursal) · el propio usuario | — |
| `PUT /usuarios/{id}`, `PATCH .../activar`, `PATCH .../desactivar` | ADMIN_SISTEMA · ADMIN_SUCURSAL (CAJERO/CHOFER de su sucursal) | UsuarioActualizado |
| `POST /usuarios/{id}/reenviar-activacion` | igual que arriba | EnlaceContrasenaEmitido(ACTIVACION) |
| `PUT /usuarios/yo` | autenticado | UsuarioActualizado |
| `POST /admin/republicar-eventos` | ADMIN_SISTEMA | SucursalCreada, UsuarioCreado |

No existe `DELETE`: los usuarios y las sucursales se desactivan.

### Errores

Todas las respuestas de error (incluidos 401 y 403 de los filtros de seguridad) tienen el mismo formato:

```json
{ "timestamp": "2026-10-07T10:15:30", "status": 422, "error": "La sucursal debe conservar al menos un administrador de sucursal activo",
  "codigo": "ULTIMO_ADMIN_SUCURSAL", "ruta": "/v1/identity/usuarios/…/desactivar" }
```

## Reglas importantes

- **Último administrador.** No se puede desactivar el último ADMIN_SISTEMA activo ni el último ADMIN_SUCURSAL
  activo de una sucursal. El servicio lo valida (422) y los triggers de PostgreSQL quedan como respaldo ante
  solicitudes simultáneas.
- **Contraseñas.** BCrypt. Política: 8 a 72 caracteres, con mayúscula, minúscula y número. La nueva debe ser
  distinta de la actual.
- **Enlaces de contraseña.** 32 bytes aleatorios en Base64 URL-safe. En la base solo se guarda su SHA-256.
  El token en claro solo viaja en el evento `EnlaceContrasenaEmitido`. Al emitir uno nuevo se invalidan los
  anteriores del mismo propósito.
- **Login.** Primero se valida la contraseña y luego si el usuario está activo. Un usuario inactivo solo recibe
  403 si su contraseña es correcta; con contraseña incorrecta recibe 401, como cualquiera.
- **Outbox.** Cada evento se guarda en `outbox_evento` en la misma transacción que el cambio, después de
  `flush()`, para que `versionAgregado` sea la versión ya incrementada. La librería lo publica cada segundo y
  borra lo publicado hace más de 24 h (3:30 a. m.).
- **Job propio.** `LimpiezaTokensJob` corre a diario a las 3:00 a. m. y borra los tokens vencidos hace más de
  7 días.
- **Correlación.** La cabecera `X-Correlation-Id` (o una nueva) se copia a cada evento y a los logs.

## Pruebas

```bash
./mvnw verify   # unitarias + integración (Testcontainers: requiere Docker) + JaCoCo ≥ 85 %
```

- Unitarias (Mockito) de cada `services/impl`, del handler, de los eventos, del job y de las utilidades.
- Integración con PostgreSQL 16 y RabbitMQ reales (`integracion/`). Cubren:
  - la creación de una sucursal con su administrador;
  - la regla del último administrador, por servicio y por trigger;
  - el flujo completo de restablecimiento y el límite de 3 por hora;
  - que el evento queda en el outbox con la llave correcta y llega a RabbitMQ;
  - la seguridad por rol (401 y 403).
- Reporte de cobertura: `target/site/jacoco/index.html`.

## Postman

`postman/identidad.postman_collection.json` usa las variables `{{baseUrl}}` y `{{token}}`. Cada petición *Login*
guarda el token automáticamente. Para probar el restablecimiento en local, el token del enlace se obtiene del
outbox:

```sql
SELECT datos ->> 'token' FROM outbox_evento
 WHERE tipo_evento = 'EnlaceContrasenaEmitido' ORDER BY creado_en DESC LIMIT 1;
```
