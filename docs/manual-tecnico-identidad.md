# Manual técnico — servicio-identidad (Identidad y Administración)

Microservicio `IdentityService` de TransportesGT. Hace de servidor de autenticación del sistema y gestiona:
- usuarios y roles;
- sucursales;
- el inicio de sesión y la emisión del JWT que validan todos los demás servicios;
- el ciclo de las contraseñas: activación de cuentas nuevas, cambio, "olvidé mi contraseña" y restablecimiento.

Fuentes de verdad: `modelo-datos-postgresql-v4.md` (tablas de Identidad), `catalogo-eventos-es-v4.md` (eventos de
Identidad) y `plan-de-trabajo.md` (endpoints y reglas de los administradores).

| | |
|---|---|
| Stack | Java 21 · Spring Boot 4.1.1 · Spring Data JPA (Hibernate) · Flyway · Spring AMQP · Spring Security (login con BCrypt y resource server JWT) · MapStruct · springdoc |
| Librería de eventos | `com.github.luisbaquiax:EventosComunesTrasnportesGt:v2.1.1` |
| Puerto / ruta base | `8081` / `/v1/identity` |
| Base de datos | PostgreSQL `bd_identidad` (usuario `svc_identidad`) |
| Broker | RabbitMQ, exchange topic `transporte.eventos` (solo publica) |

Migraciones Flyway:

| Migración | Contenido |
|---|---|
| `V1__esquema_inicial.sql` | Tablas, índices y triggers |
| `V2__datos_iniciales.sql` | Administrador de sistema y 3 sucursales con su administrador, cajero y chofer; dos clientes |

### Usuarios de prueba

Los mismos que documenta el `README` del servicio:

| Rol | Correo | Contraseña |
|---|---|---|
| ADMIN_SISTEMA | `admin@transportes.gt` | `Admin1234` |
| ADMIN_SUCURSAL | `admin.xela@transportes.gt`, `admin.capital@transportes.gt`, `admin.huehue@transportes.gt` | `Admin1234` |
| CAJERO | `cajero.xela@transportes.gt`, `cajero.capital@transportes.gt`, `cajero.huehue@transportes.gt` | `Cajero1234` |
| CHOFER | `chofer101@transportes.gt` a `chofer109@transportes.gt` (uno por chofer de Flota) | `Chofer1234` |
| CLIENTE | `cliente@transportes.gt`, `cliente2@transportes.gt` | `Cliente1234` |

Los `INSERT` de V2 no generan eventos: cada servicio trae en su propia V2 las proyecciones de estos usuarios y
sucursales, con los mismos IDs y versión 0 (`datos-semilla.md`). `POST /admin/republicar-eventos` queda para recuperar
una proyección.

---

## 1. Diagrama de tablas

```mermaid
erDiagram
    sucursal ||--o{ usuario : "id_sucursal"
    usuario ||--o{ usuario : "creado_por"
    usuario ||--o{ token_contrasena : "id_usuario"

    outbox_evento {
        uuid id PK "= idEvento del sobre"
        varchar tipo_agregado
        uuid id_agregado
        varchar tipo_evento
        varchar llave_ruteo
        bigint version_agregado
        uuid id_correlacion
        jsonb datos
        timestamptz creado_en
        timestamptz publicado_en
        int intentos
    }
    evento_procesado {
        uuid id_evento PK
        varchar consumidor PK "nombre del listener"
        timestamptz procesado_en
    }
    sucursal {
        uuid id PK
        varchar nombre UK
        varchar departamento
        varchar direccion
        varchar telefono
        numeric latitud
        numeric longitud
        boolean activa
        bigint version
        timestamptz creado_en
        timestamptz actualizado_en
    }
    usuario {
        uuid id PK
        varchar correo UK
        varchar hash_contrasena
        varchar nombre_completo
        varchar rol "ADMIN_SISTEMA | ADMIN_SUCURSAL | CAJERO | CHOFER | CLIENTE"
        uuid id_sucursal FK
        boolean activo
        uuid creado_por FK "quién lo creó (auditoría)"
        bigint version
        timestamptz contrasena_cambiada_en "auditoría del último cambio de contraseña"
        timestamptz creado_en
        timestamptz actualizado_en
    }
    token_contrasena {
        uuid id PK
        uuid id_usuario FK
        varchar hash_token UK "SHA-256 (hex) del token: el token en claro NO se guarda"
        varchar proposito "RESTABLECIMIENTO | ACTIVACION"
        timestamptz expira_en "nunca se modifica después de crear el token"
        timestamptz usado_en "se llena al consumirlo"
        timestamptz invalidado_en "se llena cuando otro token más nuevo lo reemplaza"
        varchar ip_solicitud "auditoría (IPv4 o IPv6)"
        timestamptz creado_en
    }
```

| Restricción | Garantiza |
|---|---|
| `uq_usuario_correo` sobre `lower(correo)` | Un correo por usuario, sin importar mayúsculas |
| `ck_usuario_sucursal` | El personal de sucursal tiene sucursal; SIS y CLIENTE no |
| `trg_usuario_sin_delete`, `trg_sucursal_sin_delete` | Usuarios y sucursales no se eliminan: solo se desactivan |
| `trg_proteger_ultimo_admin` (bloqueo `pg_advisory_xact_lock(1001)`) | Siempre queda al menos un ADMIN_SISTEMA activo, incluso con desactivaciones simultáneas |
| `trg_proteger_ultimo_admin_sucursal` (bloqueo por sucursal) | Cada sucursal conserva al menos un ADMIN_SUCURSAL activo (al desactivarlo, cambiarle el rol o moverlo) |
| `trg_admin_sucursal_creador` | Solo un ADMIN_SISTEMA activo crea administradores de sucursal |
| `uq_token_activo (id_usuario, proposito)` parcial | Un solo enlace vigente por usuario y propósito |
| `ck_token_vigencia`, `ck_token_estado` | El token vence después de crearse y no puede estar usado e invalidado a la vez |

---

## 2. Diagrama de componentes

```mermaid
flowchart LR
    subgraph HTTP["controllers"]
        AUC[AuthController<br/>login · registro · contraseñas]
        USC[UsuarioController]
        SUC[SucursalController]
        ADC[AdminController<br/>republicar eventos]
    end
    subgraph Seguridad["config/security"]
        JWS[JwtService<br/>firma HS256]
        UDS[CustomUserDetailsService<br/>+ BCrypt]
    end
    subgraph Servicios["services (interfaz + impl)"]
        SES[sesion<br/>SesionService]
        REG[registro<br/>RegistroClienteService]
        CON[contrasena<br/>ContrasenaService · EnlacesContrasenaService<br/>+ ValidadorContrasena]
        USU[usuario<br/>GestionUsuariosService · AltaPersonalService<br/>+ ValidadorUsuario · ReglasAdministradores]
        SUCS[sucursal<br/>GestionSucursalesService<br/>+ ValidadorSucursal]
        CQ[consulta<br/>ConsultaIdentidadService]
        ADM[admin<br/>RepublicacionEventosService]
        COM[comun · autorizacion<br/>BuscadorIdentidad · AutorizacionUsuarios]
    end
    subgraph Repos["repositories"]
        RU[UsuarioRepository<br/>+ UsuarioSpecs]
        RS[SucursalRepository<br/>+ SucursalSpecs]
        RT[TokenContrasenaRepository]
    end
    subgraph Eventos["eventos"]
        EV[EventosIdentidad<br/>arma los payloads]
        RO[[RegistroOutbox<br/>librería]]
    end
    JOB[jobs/LimpiezaTokensJob]
    AUC --> SES & REG & CON
    USC --> USU & CQ
    SUC --> SUCS & CQ
    ADC --> ADM
    SES --> JWS
    SES --> UDS
    JOB --> CON
    SUCS --> USU
    USU & SUCS & CQ & SES & CON --> COM
    COM & REG & USU & SUCS & CQ & ADM --> RU & RS
    CON --> RT
    REG & CON & USU & SUCS & ADM --> EV --> RO
    RU & RS & RT & RO --> DB[(bd_identidad)]
    RO -.->|PublicadorOutbox| MQ{{RabbitMQ}}
```

| Capa | Paquete | Responsabilidad |
|---|---|---|
| Web | `controllers` | Contrato HTTP, `@Valid`, `@PreAuthorize` por operación, paginación de Spring. Solo dependen de interfaces |
| Seguridad | `config.security` | `SecurityConfig` (rutas públicas de `/auth`, reglas por rol), `JwtService`, `JwtConfig` (HS256), `CustomUserDetailsService`, `ConvertidorUsuarioAutenticado` (claims → `UsuarioAutenticado`) y respuestas 401/403 en JSON |
| Negocio | `services.<tema>` con su `impl` | Una interfaz por caso de uso. **sesion**: login y perfil. **registro**: autorregistro de clientes. **contrasena**: cambio, olvido, validación y restablecimiento (`ValidadorContrasena`) y los enlaces de un solo uso (`EnlacesContrasenaService`). **usuario**: alta de personal (`ValidadorUsuario`) y gestión (`ReglasAdministradores`: último ADMIN_SISTEMA y último ADMIN_SUCURSAL). **sucursal**: alta con su primer administrador, edición y estado (`ValidadorSucursal`). **consulta**: listados y detalle. **admin**: republicar eventos |
| Reglas compartidas | `services.autorizacion`, `services.comun` | `AutorizacionUsuarios` (qué rol crea a quién, quién gestiona y quién ve a quién, sucursal visible en el listado) y `BuscadorIdentidad` (404 de usuario o sucursal) |
| Job | `jobs` | `LimpiezaTokensJob` (cron `identidad.jobs.limpieza-tokens-cron`) delega en `EnlacesContrasenaService.limpiarVencidos`: borra los tokens que vencieron hace más de 7 días (`identidad.retencion-tokens`) |
| Utilidades | `utils` | `NormalizadorCorreo`, `ResolvedorIpCliente` (auditoría) y `GeneradorTokenSeguro`, todos `@Component` |
| Eventos | `eventos` | `EventosIdentidad` arma los payloads y los registra en el outbox |
| Datos | `models`, `repositories` | Entidades (`ddl-auto=validate`) y `UsuarioSpecs`/`SucursalSpecs` (`@Component`) |
| Errores | `exception` | Jerarquía `IdentityServiceException`, `GlobalIdentityServiceExceptionHandler`, `FabricaRespuestaError` y `TraductorErroresBd` (traduce únicos, checks y triggers de PostgreSQL) |

No hay métodos estáticos en las clases del servicio: los helpers son `@Component` inyectables.

### 2.1 Endpoints y permisos

| Método | Ruta | Quién | Evento |
|---|---|---|---|
| POST | `/auth/login` | Público | — (devuelve el JWT y el usuario) |
| POST | `/auth/registro` | Público | `UsuarioCreado` (rol CLIENTE) |
| GET | `/auth/yo` | Autenticado | — |
| POST | `/auth/cambiar-contrasena` | Autenticado | `ContrasenaCambiada` (CAMBIO) |
| POST | `/auth/olvide-contrasena` | Público, siempre 202, máx. 3 por hora | `EnlaceContrasenaEmitido` (RESTABLECIMIENTO) |
| GET | `/auth/enlace-contrasena/validar?token=` | Público | — (200 con el correo enmascarado, o 410 `ENLACE_INVALIDO`) |
| POST | `/auth/restablecer-contrasena` | Público | `ContrasenaCambiada` |
| POST | `/sucursales` | SIS | `SucursalCreada` + `UsuarioCreado` del primer administrador + su enlace de activación |
| GET | `/sucursales`, `/sucursales/{id}` | Autenticado | — |
| PUT / PATCH | `/sucursales/{id}`, `/{id}/activar`, `/{id}/desactivar` | SIS | `SucursalActualizada` |
| GET | `/sucursales/{id}/administradores` | SIS | — |
| POST | `/usuarios` | SIS (crea ADMIN_SISTEMA y ADMIN_SUCURSAL), SUC (crea CAJERO y CHOFER de su sucursal) | `UsuarioCreado` + `EnlaceContrasenaEmitido` (ACTIVACION) |
| GET | `/usuarios`, `/usuarios/{id}` | SIS, SUC (su sucursal); cada usuario el suyo | — |
| PUT | `/usuarios/{id}`, `/usuarios/yo` | Administradores / el propio usuario | `UsuarioActualizado` |
| PATCH | `/usuarios/{id}/activar`, `/desactivar` | SIS, SUC | `UsuarioActualizado` |
| POST | `/usuarios/{id}/reenviar-activacion` | SIS, SUC | `EnlaceContrasenaEmitido` |
| POST | `/admin/republicar-eventos` | SIS | Republica `SucursalCreada` y `UsuarioCreado` de todo lo existente |

**JWT:**
- HS256, emisor `servicio-identidad`, vigencia `JWT_EXPIRATION` (1 h por omisión).
- Claims: `sub` = id del usuario, `correo`, `nombre`, `rol` (sin prefijo) e `idSucursal` (solo el personal de
  sucursal).
- Los demás servicios lo validan con el mismo secreto y emisor y mapean el rol a `ROLE_<rol>`.

---

## 3. Inicio de sesión

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuario
    participant A as SesionService
    participant DB as bd_identidad
    participant J as JwtService

    U->>A: POST /auth/login { correo, contrasena }
    A->>DB: usuario por lower(correo)
    alt No existe o la contraseña no coincide (BCrypt)
        A-->>U: 401 CREDENCIALES_INVALIDAS (mismo mensaje en ambos casos)
    else Usuario inactivo
        A-->>U: 403 CUENTA_INACTIVA
    else
        A->>J: emitir(usuario)
        J-->>A: JWT HS256 (sub, correo, nombre, rol, idSucursal)
        A-->>U: 200 { token, expiraEn, usuario }
    end
    Note over U: El frontend envía Authorization: Bearer en cada servicio.<br/>Un 401 con token cierra la sesión.
```

---

## 4. Enlaces de contraseña (activación y restablecimiento)

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuario
    participant S as ContrasenaService / AltaPersonalService
    participant T as EnlacesContrasenaService
    participant DB as bd_identidad
    participant MQ as RabbitMQ
    participant N as Notificaciones

    alt Personal nuevo (POST /usuarios o /sucursales)
        S->>T: emitir(usuario, ACTIVACION)
    else POST /auth/olvide-contrasena
        S->>DB: ¿correo activo y menos de 3 solicitudes en la última hora?
        Note over S: Siempre responde 202: no revela si el correo existe
        S->>T: emitir(usuario, RESTABLECIMIENTO)
    end
    T->>T: token aleatorio en claro · se guarda solo SHA-256
    T->>DB: invalida el enlace vigente del mismo propósito + INSERT token_contrasena
    T->>DB: outbox EnlaceContrasenaEmitido (lleva el token en claro y expiraEn)
    MQ->>N: correo con el enlace (vigencia 48 h activación / 30 min restablecimiento)
    U->>S: GET /auth/enlace-contrasena/validar?token
    S-->>U: 200 { proposito, correo enmascarado } · 410 ENLACE_INVALIDO si venció, se usó o se invalidó
    U->>S: POST /auth/restablecer-contrasena { token, nuevaContrasena }
    S->>DB: nueva contraseña BCrypt (distinta de la actual) · token usado · invalida los demás
    S->>DB: outbox ContrasenaCambiada (ACTIVACION o RESTABLECIMIENTO)
```

- **Activación:** el personal creado por un administrador no recibe contraseña. La define con el enlace de
  activación, que se puede reenviar con `POST /usuarios/{id}/reenviar-activacion`.
- **Registro de clientes:** el autorregistro (`/auth/registro`) crea un CLIENTE con contraseña. Clientes y Cartera
  crea su perfil al consumir `UsuarioCreado`.
- **Desactivación:** `UsuarioActualizado` con `activo = false` bloquea la cartera en Clientes y Cartera. Los
  triggers impiden quedarse sin administradores.

| Evento publicado | Llave | Consumidores |
|---|---|---|
| `UsuarioCreado` / `UsuarioActualizado` | `identidad.usuario.creado` / `.actualizado` | Clientes y Cartera, Notificaciones |
| `SucursalCreada` / `SucursalActualizada` | `identidad.sucursal.creada` / `.actualizada` | Flota, Viajes, Alquileres, Costos, Reportes |
| `EnlaceContrasenaEmitido` | `identidad.usuario.enlace-contrasena-emitido` | Notificaciones |
| `ContrasenaCambiada` | `identidad.usuario.contrasena-cambiada` | Notificaciones |

Identidad no consume eventos.

---

## 5. Diagrama de despliegue

Producción en Google Cloud (proyecto `unimarketcunoc`). En local, `infra/docker-compose.yml` levanta lo mismo con
PostgreSQL y RabbitMQ en contenedores.

```mermaid
flowchart TB
    U([Navegador]) -->|HTTPS| FE[Frontend React<br/>Vercel · transporte-gt-01.vercel.app]
    FE -->|HTTPS + JWT| GW

    subgraph CR["Cloud Run · northamerica-south1"]
        GW[gateway-service<br/>Spring Cloud Gateway · público]
        SV[identity-service<br/>Java 21 · emite el JWT · 1 vCPU · 2 GiB<br/>min 1 · max 1 instancia]
        NO[notification-service<br/>correos de enlaces]
    end

    subgraph Datos["Servicios administrados"]
        SQL[(Cloud SQL PostgreSQL · us-east1<br/>transportesgtdb · bd_identidad)]
        MQ{{CloudAMQP · RabbitMQ<br/>amqps:// · exchange transporte.eventos}}
    end

    subgraph CI["CI/CD"]
        GH[GitHub<br/>luisbaquiax/TransportesGT · main]
        CB[Cloud Build<br/>activador identity-service]
        AR[(Artifact Registry<br/>cloud-run-source-deploy/transportesgt/identity-service)]
    end

    GW -->|/v1/identity/**| SV
    SV -->|Cloud SQL socket factory| SQL
    SV <-->|eventos outbox / colas| MQ
    MQ -->|EnlaceContrasenaEmitido| NO
    SV -.->|JWT HS256| FE
    GH -->|push / merge a main| CB
    CB -->|docker build IdentityService/| AR
    CB -->|gcloud run services update| SV
    AR -.->|imagen :COMMIT_SHA| SV
```

| Elemento | Detalle |
|---|---|
| Imagen | `Dockerfile` en dos etapas: `maven:3.9-eclipse-temurin-21` compila (`./mvnw clean package`) y `bellsoft/liberica-openjdk-alpine:21` ejecuta `app.jar` |
| CI/CD | Cada merge a `main` dispara el activador de Cloud Build: `docker build IdentityService -f IdentityService/Dockerfile`, push a Artifact Registry con la etiqueta del commit y `gcloud run services update identity-service` |
| Base de datos | Cloud SQL `unimarketcunoc:us-east1:transportesgtdb`, base `bd_identidad`, por la *socket factory* (`jdbc:postgresql:///bd_identidad?cloudSqlInstance=…&socketFactory=…`) |
| Configuración | Variables de entorno: `SPRING_PROFILES_DEFAULT=prod`, `SPRING_DATASOURCE_*`, `RABBITMQ_URL`, `JWT_SECRET` (el mismo de todos los servicios), `JWT_EXPIRATION`, `IDENTIDAD_VIGENCIA_ACTIVACION`, `IDENTIDAD_VIGENCIA_RESTABLECIMIENTO`, `CORS_ALLOWED_ORIGINS`, `SERVER_PORT=8080`, `SWAGGER_HABILITADO`, `DB_POOL_MAX` |
| Esquema | Flyway aplica las migraciones al arrancar; Hibernate solo valida. En la nube no se modifican migraciones ya aplicadas: los cambios van en una versión nueva |
| Instancias | Exactamente 1 (`min` y `max`). Varias serían seguras: el outbox y `evento_procesado` hacen idempotente el consumo |
| CPU | Hoy con CPU solo durante peticiones (valor por defecto). Para que los consumidores procesen eventos sin tráfico HTTP conviene `--no-cpu-throttling` |
| Publica | `SucursalCreada/Actualizada`, `UsuarioCreado/Actualizado`, `EnlaceContrasenaEmitido`, `ContrasenaCambiada`. No consume eventos |
| Acceso | Público (`ingress=all`, sin IAM de invocación); los datos los protege el JWT. Pendiente: dejar público solo el gateway |
| Cuenta de servicio | La de Compute por defecto (`…-compute@developer.gserviceaccount.com`), compartida con los demás servicios |
| Salud | `/v1/identity/actuator/health` (`liveness` y `readiness`) |
