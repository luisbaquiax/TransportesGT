# Manual técnico — servicio-notificaciones

Microservicio `NotificationService` de TransportesGT. Envía todos los correos del sistema a partir de los eventos de
los demás servicios: confirmaciones de compra, avisos de cartera y de alquileres, enlaces y avisos de contraseña, y
avisos de vencimiento de licencia. **Solo consume eventos**: no publica eventos ni llama a otros servicios. Fuentes de
verdad: `modelo-datos-postgresql-v4.md` §8 (tablas y reglas) y `catalogo-eventos-es-v4.md` §2.1, §2.2, §2.4–§2.6, §3 y
§4.5.

| | |
|---|---|
| Stack | Java 21 · Spring Boot 4.1.1 · Spring Data JPA (Hibernate) · Flyway · Spring AMQP · Spring Security (resource server JWT) · Spring Mail · Thymeleaf · springdoc |
| Librería de eventos | `com.github.luisbaquiax:EventosComunesTrasnportesGt:v2.1.1` (sobre, payloads, consumidor idempotente, topología) |
| Puerto / ruta base | `8088` / `/v1/notifications` |
| Base de datos | PostgreSQL `bd_notificaciones` |
| Broker | RabbitMQ, exchange topic `transporte.eventos`, DLX `transporte.eventos.dlx` |
| Correo | SMTP con STARTTLS (587). En local, Mailpit; en las pruebas, GreenMail |

Tablas: `contacto` y `chofer_licencia` (proyecciones, solo las modifican los consumidores), `recordatorio_licencia`,
`notificacion` (bitácora y bandeja de salida), `evento_procesado` y `outbox_evento`. Esta última queda vacía, pero
existe porque el publicador del outbox de la librería se registra siempre y la consulta cada segundo.

---

## 1. Diagrama de tablas

Generado de `V1__esquema_inicial.sql`. `contacto` y `chofer_licencia` son proyecciones de Identidad y Flota; `notificacion` es la bandeja de salida y la bitácora de cada correo.

```mermaid
erDiagram
    chofer_licencia ||--o{ recordatorio_licencia : "id_chofer"

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
    contacto {
        uuid id_usuario PK
        varchar correo
        varchar nombre_completo
        varchar rol
        uuid id_sucursal
        boolean activo
        bigint version
    }
    chofer_licencia {
        uuid id_chofer PK
        uuid id_sucursal
        varchar nombre_completo
        varchar correo
        date fecha_vencimiento_licencia
        boolean activo
        bigint version
    }
    recordatorio_licencia {
        uuid id PK
        uuid id_chofer FK
        date fecha_vencimiento
        smallint dias_anticipacion "30 | 15 | 3"
        date fecha_envio_programada
        varchar estado "PENDIENTE | ENVIADO | CANCELADO | FALLIDO"
        smallint intentos
        timestamptz enviado_en
        timestamptz creado_en
    }
    notificacion {
        uuid id PK
        varchar tipo "COMPRA_BOLETOS, LICENCIA_POR_VENCER, ALQUILER_COTIZADO, ENLACE_CONTRAS"
        varchar correo_destino
        uuid id_usuario
        varchar asunto
        text cuerpo
        varchar estado "PENDIENTE | ENVIADA | FALLIDA"
        smallint intentos
        text ultimo_error
        timestamptz proximo_intento_en
        varchar clave_dedup UK "'evt:'idEvento':'correo'' o 'lic:'idRecordatorio':'correo''"
        uuid id_evento_origen
        timestamptz creado_en
        timestamptz enviada_en
    }
```

## 2. Diagrama de componentes

```mermaid
flowchart LR
    subgraph HTTP["controllers"]
        NC[NotificacionController<br/>bitácora y reintento]
        LC[LicenciaController<br/>avisos de licencia]
        PC[ProcesamientoController<br/>interno/procesar]
    end
    subgraph Servicios["services (interfaz + impl)"]
        BAN[bandeja<br/>BandejaSalida<br/>+ Destinatarios · ClaveDedup]
        ENV[envio<br/>EnvioCorreosService<br/>+ GestorEnvio · PoliticaReintentos]
        ENL[enlace<br/>EnlaceContrasenaService<br/>+ ConstructorEnlaces]
        LIC[licencia<br/>AvisosLicenciaService<br/>+ GestorAvisoLicencia]
        BIT[bitacora<br/>BitacoraService · ConsultaLicenciasService]
        PRO[proyeccion<br/>ProyeccionContactosService<br/>ProyeccionChoferesService + CalendarioRecordatorios]
    end
    subgraph Correo["correo"]
        REN[[RenderizadorCorreo]] -.-> TRC[ThymeleafRenderizadorCorreo]
        ENC[[EnviadorCorreo]] -.-> SMC[SmtpEnviadorCorreo]
    end
    subgraph Repos["repositories"]
        RNO[NotificacionRepository<br/>+ NotificacionSpecs]
        RCO[ContactoRepository]
        RCH[ChoferLicenciaRepository<br/>RecordatorioLicenciaRepository]
    end
    subgraph Eventos["eventos"]
        MCO[correo/<br/>9 manejadores de correo<br/>+ CorreosDeEvento]
        MEN[correo/ManejadorEnlaceContrasenaEmitido<br/>envío directo]
        MPR[proyeccion/<br/>Usuario* · Chofer*]
        DE[[DespachadorEventos<br/>librería]]
    end
    subgraph Jobs["jobs"]
        JE[EnvioCorreosJob]
        JA[AvisosLicenciaJob]
    end
    NC & LC --> BIT
    PC --> ENV & LIC
    JE --> ENV
    JA --> LIC
    DE --> MCO & MEN & MPR
    MCO --> BAN
    MEN --> ENL
    MPR --> PRO
    LIC --> BAN
    BAN --> REN
    ENL --> REN & ENC
    ENV --> ENC
    BAN & ENV & ENL & LIC & BIT & PRO --> RNO & RCO & RCH
    RNO & RCO & RCH --> DB[(bd_notificaciones)]
    SMC --> SMTP[Servidor SMTP]
```

| Capa | Paquete | Responsabilidad |
|---|---|---|
| Web | `controllers` | Bitácora, reintento, avisos de licencia y ejecución manual de los jobs |
| Seguridad | `config.security` | Validar JWT; reglas: `GET /notificaciones/licencias` → SIS y SUC, el resto → SIS |
| Eventos | `eventos.proyeccion`, `eventos.correo` | Un manejador por evento (un evento nuevo = un manejador nuevo, sin tocar los demás). Validan, eligen destinatarios y delegan |
| Negocio | `services.<tema>` (+ `impl`) | `proyeccion` (copias locales y programación de avisos), `bandeja` (encolar `PENDIENTE`), `enlace` (envío directo), `envio` (job de envío), `licencia` (job de avisos), `bitacora` (consultas y reintento) |
| Correo | `correo` | Puertos `RenderizadorCorreo` y `EnviadorCorreo` con sus adaptadores Thymeleaf y SMTP: los servicios no dependen de JavaMail ni de Thymeleaf |
| Datos | `models`, `repositories` | Entidades (`ddl-auto=validate`), *upserts* por versión, `ON CONFLICT DO NOTHING`, `FOR UPDATE SKIP LOCKED` |
| Errores | `exception` | Jerarquía `NotificationServiceException` y `RespuestaError` uniforme |

---

## 3. Flujo del correo: consumidor → PENDIENTE → job → SMTP

```mermaid
sequenceDiagram
    autonumber
    participant MQ as RabbitMQ
    participant D as DespachadorEventos
    participant M as Manejador de correo
    participant B as BandejaSalida
    participant DB as bd_notificaciones
    participant J as EnvioCorreosJob (30 s)
    participant G as GestorEnvio
    participant S as SMTP

    MQ->>D: p. ej. CarteraRecargada (notificaciones.cartera.q)
    D->>DB: BEGIN · INSERT evento_procesado ON CONFLICT DO NOTHING
    alt idEvento ya procesado
        D->>DB: COMMIT (duplicado ignorado)
    else Evento nuevo
        D->>M: manejar(sobre)
        M->>DB: contacto del destinatario
        alt Sin contacto (su UsuarioCreado no llegó)
            M-->>D: ContactoNoDisponibleException
            D->>DB: ROLLBACK → RabbitMQ reintenta (1, 2, 4 s) y luego DLQ
        else Con contacto
            M->>B: encolar(tipo, destinatario, datos, evt:{idEvento}:{correo})
            B->>B: Thymeleaf: asunto (title) + HTML
            B->>DB: INSERT notificacion PENDIENTE ON CONFLICT (clave_dedup) DO NOTHING
            D->>DB: COMMIT (procesado + correo pendiente, juntos)
        end
    end

    loop Cada 30 s, hasta 20 correos
        J->>G: enviarSiguiente()
        G->>DB: BEGIN · SELECT … WHERE estado = 'PENDIENTE' AND proximo_intento_en <= now<br/>LIMIT 1 FOR UPDATE SKIP LOCKED
        G->>S: HTML + texto plano
        alt Aceptado
            G->>DB: ENVIADA, enviada_en · COMMIT
        else Falla (intentos < 5)
            G->>DB: intentos++, ultimo_error, proximo_intento_en = now + 1/2/4/8 min · COMMIT
        else 5.º fallo
            G->>DB: FALLIDA · COMMIT
        end
    end
    Note over G,DB: Un correo por transacción: un fallo no afecta a los demás.<br/>El correo nunca revierte la operación de negocio que lo originó.
```

- **Idempotencia doble:** `evento_procesado` evita reprocesar un evento ya confirmado, y `clave_dedup` es el
  respaldo dentro de la base: el mismo `idEvento` nunca produce dos correos al mismo destinatario. Un evento con otro
  `idEvento` es otro hecho y sí genera su propio correo.
- **Varias instancias:** `SKIP LOCKED` hace que cada instancia tome filas distintas; la fila queda bloqueada mientras
  se envía.
- **Reintento manual:** `POST /notificaciones/{id}/reintentar` deja un `FALLIDA` en `PENDIENTE` con 0 intentos.

---

## 4. Envío directo de EnlaceContrasenaEmitido

```mermaid
sequenceDiagram
    autonumber
    participant MQ as RabbitMQ
    participant D as DespachadorEventos
    participant E as EnlaceContrasenaService
    participant DB as bd_notificaciones
    participant S as SMTP

    MQ->>D: EnlaceContrasenaEmitido (token en claro)
    D->>DB: BEGIN · INSERT evento_procesado
    D->>E: enviar(idEvento, datos)
    E->>DB: ¿notificacion ENVIADA con clave evt:{idEvento}:{correo}?
    alt Ya enviada
        E-->>D: nada que hacer
    else No enviada
        E->>E: enlace = FRONTEND_URL + ruta (activar / restablecer) + ?token=…
        E->>E: Thymeleaf (botón + vigencia en hora de Guatemala)
        E->>S: correo al correo del payload
        alt SMTP falla
            E-->>D: EnvioCorreoException
            D->>DB: ROLLBACK → RabbitMQ reintenta y, al agotar, DLQ
        else Enviado
            E->>DB: INSERT notificacion ENVIADA<br/>cuerpo = '[contenido sensible no almacenado]'
        end
    end
    D->>DB: COMMIT
    Note over E,DB: El token y el enlace no se guardan ni se escriben en logs o excepciones.<br/>No se reintenta desde la bitácora (409): el usuario pide un enlace nuevo.
```

- No pasa por la bandeja porque el cuerpo guardado tendría el secreto, y el job lo necesitaría para reenviarlo.
- Si el SMTP acepta el correo pero el `COMMIT` falla, el reintento lo envía otra vez (entrega *al menos una vez*): es
  preferible a perder el enlace.

---

## 5. Avisos de vencimiento de licencia

```mermaid
sequenceDiagram
    autonumber
    participant F as Flota
    participant P as ProyeccionChoferesService
    participant C as CalendarioRecordatorios
    participant DB as bd_notificaciones
    participant J as AvisosLicenciaJob (7:00 Guatemala)
    participant G as GestorAvisoLicencia
    participant B as BandejaSalida

    F->>P: ChoferRegistrado / ChoferActualizado
    P->>DB: upsert chofer_licencia (si versionAgregado > version)
    alt activo = false
        P->>DB: CANCELADO todos sus avisos PENDIENTE
    else activo
        P->>DB: CANCELADO los PENDIENTE de otra fecha de vencimiento
        P->>DB: CANCELADO → PENDIENTE los de la fecha vigente que aún no pasaron (reactivación)
        P->>C: avisos(vencimiento, hoy)
        C-->>P: 30, 15 y 3 días antes, sin los anteriores a hoy
        P->>DB: INSERT recordatorio_licencia ON CONFLICT (uq_recordatorio) DO NOTHING
    end

    J->>DB: avisos PENDIENTE con fecha_envio_programada <= hoy
    loop Un aviso por transacción
        J->>G: procesar(id)
        G->>DB: SELECT aviso + chofer FOR UPDATE
        alt Chofer inactivo o fecha distinta a la vigente
            G->>DB: CANCELADO
        else Vigente
            G->>B: LICENCIA_POR_VENCER al chofer (lic:{id}:{correo})
            G->>B: LICENCIA_POR_VENCER a cada ADMIN_SUCURSAL activo de su sucursal
            G->>DB: ENVIADO, enviado_en
        end
    end
    Note over J,B: Los correos quedan PENDIENTE y los manda el job de envío.<br/>Si el job se atrasó, el correo dice los días que realmente faltan.
```

"Hoy" se calcula con el `Clock` inyectado en `America/Guatemala`. El aviso cuya fecha es hoy sí se programa: lo envía
el job de hoy (o la siguiente corrida si ya pasó la de las 7:00).

---

## 6. Diagrama de despliegue

Producción en Google Cloud (proyecto `unimarketcunoc`). En local, `infra/docker-compose.yml` levanta lo mismo con
PostgreSQL y RabbitMQ en contenedores.

```mermaid
flowchart TB
    U([Navegador]) -->|HTTPS| FE[Frontend React<br/>Vercel · transporte-gt-01.vercel.app]
    FE -->|HTTPS + JWT| GW

    subgraph CR["Cloud Run · northamerica-south1"]
        GW[gateway-service<br/>Spring Cloud Gateway · público]
        SV[notification-service<br/>Java 21 · Thymeleaf · 1 vCPU · 1 GiB<br/>min 1 · max 1 instancia]

    end

    subgraph Datos["Servicios administrados"]
        SQL[(Cloud SQL PostgreSQL · us-east1<br/>transportesgtdb · bd_notificaciones)]
        MQ{{CloudAMQP · RabbitMQ<br/>amqps:// · exchange transporte.eventos}}
    end

    subgraph CI["CI/CD"]
        GH[GitHub<br/>luisbaquiax/TransportesGT · main]
        CB[Cloud Build<br/>activador notification-service]
        AR[(Artifact Registry<br/>cloud-run-source-deploy/transportesgt/notification-service)]
    end

    GW -->|/v1/notifications/**| SV
    SV -->|Cloud SQL socket factory| SQL
    SV <-->|eventos outbox / colas| MQ
    SV -->|SMTP 587 STARTTLS| SMTP[Gmail SMTP<br/>smtp.gmail.com]
    SMTP -.->|correo con enlace a FRONTEND_URL| U
    GH -->|push / merge a main| CB
    CB -->|docker build NotificationService/| AR
    CB -->|gcloud run services update| SV
    AR -.->|imagen :COMMIT_SHA| SV
```

| Elemento | Detalle |
|---|---|
| Imagen | `Dockerfile` en dos etapas: `maven:3.9-eclipse-temurin-21` compila (`./mvnw clean package`) y `bellsoft/liberica-openjdk-alpine:21` ejecuta `app.jar` |
| CI/CD | Cada merge a `main` dispara el activador de Cloud Build: `docker build NotificationService -f NotificationService/Dockerfile`, push a Artifact Registry con la etiqueta del commit y `gcloud run services update notification-service` |
| Base de datos | Cloud SQL `unimarketcunoc:us-east1:transportesgtdb`, base `bd_notificaciones`, por la *socket factory* (`jdbc:postgresql:///bd_notificaciones?cloudSqlInstance=…&socketFactory=…`) |
| Configuración | Variables de entorno: `SPRING_PROFILES_DEFAULT=prod`, `SPRING_DATASOURCE_*`, `RABBITMQ_URL`, `JWT_SECRET` (el mismo de todos los servicios), `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_REMITENTE`, `FRONTEND_URL`, `JWT_ISSUER`, `SERVER_PORT=8080`, `SWAGGER_HABILITADO`, `DB_POOL_MAX` |
| Esquema | Flyway aplica las migraciones al arrancar; Hibernate solo valida. En la nube no se modifican migraciones ya aplicadas: los cambios van en una versión nueva |
| Instancias | Exactamente 1 (`min` y `max`). Varias serían seguras: el outbox y `evento_procesado` hacen idempotente el consumo |
| CPU | Hoy con CPU solo durante peticiones (valor por defecto). Sus jobs (`@Scheduled`) y los consumidores de RabbitMQ necesitan CPU sin tráfico HTTP: conviene `--no-cpu-throttling` |
| Correo | Gmail SMTP (`smtp.gmail.com:587`, STARTTLS) con contraseña de aplicación. Los enlaces de activación y restablecimiento apuntan a `FRONTEND_URL` (Vercel) |
| Jobs | El envío de correos pendientes y los avisos de licencia corren con `@Scheduled`; además `POST /interno/procesar` los dispara a demanda |
| Acceso | Público (`ingress=all`, sin IAM de invocación); los datos los protege el JWT. Pendiente: dejar público solo el gateway |
| Cuenta de servicio | La de Compute por defecto (`…-compute@developer.gserviceaccount.com`), compartida con los demás servicios |
| Salud | `/v1/notifications/actuator/health` (`liveness` y `readiness`) |
