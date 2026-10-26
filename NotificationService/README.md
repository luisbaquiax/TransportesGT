# servicio-notificaciones (NotificationService)

Correos de TransportesGT. El servicio **solo consume eventos**: no publica eventos ni llama a otros servicios.
Mantiene copias locales de contactos y choferes, programa los avisos de vencimiento de licencia y envía los correos
por SMTP con un job (patrón *bandeja de salida*): una falla del correo nunca revierte la operación que lo originó.
Usa la librería `EventosComunesTrasnportesGt` `v2.1.1`.

| | |
|---|---|
| Puerto | `8088` |
| Ruta base | `/v1/notifications` (`server.servlet.context-path`; el gateway reenvía `/v1/notifications/**` sin reescribir) |
| Swagger | <http://localhost:8088/v1/notifications/swagger-ui.html> |
| OpenAPI | <http://localhost:8088/v1/notifications/v3/api-docs> |
| Health | <http://localhost:8088/v1/notifications/actuator/health> |
| Base de datos | `bd_notificaciones` (PostgreSQL), migración Flyway `src/main/resources/db/migration/V1__esquema_inicial.sql` (modelo v4 §8) |
| Plantillas | `src/main/resources/templates/correo/` (Thymeleaf: `layout.html` + una por tipo) |

## Ejecutar en local

```bash
# 1. Infraestructura (PostgreSQL + RabbitMQ)
cd ../infra && docker compose up -d

# 2. Mailpit: servidor SMTP de prueba. Guarda los correos sin entregarlos; se ven en http://localhost:8025
docker run -d --name mailpit -p 1025:1025 -p 8025:8025 axllent/mailpit

# 3. Variables (archivo .env en esta carpeta; ver tabla y .env.example)
# 4. Arrancar (perfil dev por defecto)
./mvnw spring-boot:run
```

Con Mailpit: `MAIL_HOST=localhost`, `MAIL_PORT=1025`, `MAIL_SMTP_AUTH=false`, sin usuario ni contraseña.

### Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | sí | Conexión a `bd_notificaciones` |
| `RABBITMQ_URL` | sí | `amqp://usuario:clave@host:5672` (en la nube, `amqps://`) |
| `JWT_SECRET` | sí | Secreto HS256 en **Base64**, el mismo de Identidad y del gateway. Este servicio solo **valida** el token |
| `JWT_ISSUER` | no | Emisor esperado del claim `iss`. Por defecto `servicio-identidad` |
| `MAIL_HOST` / `MAIL_PORT` | no | SMTP. Por defecto `smtp.gmail.com` / `587` (STARTTLS; Cloud Run bloquea el 25) |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | según el SMTP | Credenciales (en Gmail, una contraseña de aplicación) |
| `MAIL_SMTP_AUTH` | no | `true` por defecto; `false` con Mailpit |
| `MAIL_REMITENTE` | no | `From` de los correos. Por defecto `Transportes GT <no-responder@transportes.gt>` |
| `FRONTEND_URL` | sí en producción | Base de los enlaces de activación y restablecimiento. Por defecto `http://localhost:5173` |
| `SERVER_PORT` | no | Por defecto `8088` |
| `SPRING_PROFILES_ACTIVE` | no | `dev` (por defecto) o `prod` |

> Las variables `SPRING_MAIL_*` del `.env` anterior ya no se leen: renómbrelas a `MAIL_*`.

Otras propiedades (`application.properties`):
- enlaces: `notificaciones.ruta-restablecer` (`/restablecer-contrasena`) y `notificaciones.ruta-activar` (`/activar-cuenta`);
- envío: `notificaciones.envio.lote` (20), `.intervalo-ms` (30 000), `.max-intentos` (5) y `.espera-inicial` (1 min);
- avisos: `notificaciones.licencias.cron` (`0 0 7 * * *`, en hora de Guatemala).

## Endpoints

| Método y ruta | Quién | Respuesta |
|---|---|---|
| `GET /notificaciones?estado=&tipo=&desde=&hasta=&pagina=&tamano=` | ADMIN_SISTEMA | Bitácora paginada `{ contenido, pagina, tamano, totalElementos, totalPaginas }`, más reciente primero. **Sin el cuerpo**: asunto, destinatario, estado, intentos, `ultimoError` y fechas. `desde`/`hasta` son días en hora de Guatemala |
| `GET /notificaciones/{id}` | ADMIN_SISTEMA | Con el cuerpo HTML (`[contenido sensible no almacenado]` en `ENLACE_CONTRASENA`). 404 si no existe |
| `POST /notificaciones/{id}/reintentar` | ADMIN_SISTEMA | Solo `FALLIDA` y nunca `ENLACE_CONTRASENA` (si no → 409 `REINTENTO_NO_PERMITIDO`). Queda `PENDIENTE` con 0 intentos |
| `GET /notificaciones/licencias?idSucursal=&estado=&pagina=&tamano=` | ADMIN_SISTEMA, ADMIN_SUCURSAL | Avisos de licencia con chofer, vencimiento, anticipación, fecha programada y estado. ADMIN_SUCURSAL solo ve su sucursal; otra → 403 `SIN_PERMISO` |
| `POST /notificaciones/interno/procesar` | ADMIN_SISTEMA | Ejecuta una vez los avisos de licencia y un lote del envío. Para pruebas y Cloud Scheduler |

Errores: `{ timestamp, status, error, codigo, ruta }`.

## Eventos consumidos y correos

| Cola | Llave | Evento | Efecto |
|---|---|---|---|
| `notificaciones.identidad.q` | `identidad.usuario.creado` | UsuarioCreado | *Upsert* de `contacto` por versión. Sin correo |
| | `identidad.usuario.actualizado` | UsuarioActualizado | Igual que Creado (puede llegar antes) |
| | `identidad.usuario.enlace-contrasena-emitido` | EnlaceContrasenaEmitido | **Envío directo** del enlace al correo del payload (`ENLACE_CONTRASENA`) |
| | `identidad.usuario.contrasena-cambiada` | ContrasenaCambiada | Aviso de seguridad al correo del payload (`CONTRASENA_CAMBIADA`) |
| `notificaciones.flota.q` | `flota.chofer.registrado` | ChoferRegistrado | *Upsert* de `chofer_licencia` y avisos a 30, 15 y 3 días. Sin correo |
| | `flota.chofer.actualizado` | ChoferActualizado | Igual. Otra fecha → cancela los avisos de la anterior. Inactivo → cancela todos |
| `notificaciones.boletos.q` | `boletos.venta.registrada` | VentaBoletosRegistrada | Confirmación al comprador (`COMPRA_BOLETOS`). Mostrador sin cuenta → nada |
| | `boletos.venta.anulada` | BoletosAnulados | Aviso al comprador (`BOLETOS_ANULADOS`); menciona el reembolso si fue en línea con pago |
| `notificaciones.cartera.q` | `cartera.cartera.recargada` | CarteraRecargada | Al dueño, con monto y saldo (`CARTERA_RECARGADA`) |
| | `cartera.cartera.reembolsada` | CarteraReembolsada | Al dueño, con monto y saldo (`CARTERA_REEMBOLSADA`) |
| `notificaciones.alquileres.q` | `alquileres.alquiler.solicitado` | AlquilerSolicitado | A cada ADMIN_SUCURSAL activo de la sucursal de origen (`ALQUILER_SOLICITADO`) |
| | `alquileres.alquiler.cotizado` | AlquilerCotizado | Al cliente, si tiene cuenta: "ya puede pagar" (`ALQUILER_COTIZADO`) |
| | `alquileres.alquiler.confirmado` | AlquilerConfirmado | Al cliente (si tiene cuenta) y a los ADMIN_SUCURSAL de la sucursal (`ALQUILER_CONFIRMADO`) |
| | `alquileres.alquiler.cancelado` | AlquilerCancelado | Al cliente, si tiene cuenta; menciona el reembolso si `idPago != null` (`ALQUILER_CANCELADO`) |

Además, el job diario genera `LICENCIA_POR_VENCER` para el chofer y para cada ADMIN_SUCURSAL activo de su sucursal.

- Las llaves son **exactas**: `flota.*` traería eventos de buses y `cartera.*` traería `PerfilClienteActualizado`.
- Cada cola tiene su DLQ (`notificaciones.<productor>.dlq`, en `transporte.eventos.dlx`). Reintentos: 1 intento + 3 con
  espera de 1, 2 y 4 s. JSON inválido o dato obligatorio ausente → DLQ inmediato.
- Si el destinatario aún no está en `contacto` (su UsuarioCreado no llegó), el error es transitorio: se reintenta y, si
  al agotar sigue sin existir, va a la DLQ.

## Cómo funciona el envío

1. El consumidor **no envía**: en la misma transacción que `evento_procesado` inserta una `notificacion` `PENDIENTE`
   por destinatario, con el asunto y el HTML ya renderizados y `clave_dedup = evt:{idEvento}:{correo}`
   (`ON CONFLICT DO NOTHING`: un evento repetido no duplica el correo).
2. Cada 30 s el job toma hasta 20 `PENDIENTE` con `proximo_intento_en <= ahora`, uno por transacción con
   `FOR UPDATE SKIP LOCKED` (seguro con varias instancias), y los envía (HTML + texto plano).
3. Éxito → `ENVIADA`. Fallo → `intentos++`, `ultimo_error` (recortado y sin direcciones de correo) y
   reintento a 1, 2, 4 y 8 min. Al 5.º fallo → `FALLIDA`; se puede reintentar desde la bitácora.

**Enlace de contraseña.** Lleva un token secreto, así que se envía de inmediato desde el consumidor y en la base se
guarda `cuerpo = '[contenido sensible no almacenado]'`. Si el SMTP falla, el evento se reintenta (y termina en la DLQ).
El token y el enlace nunca van a logs, excepciones ni a la base. Un enlace no se reintenta desde la bitácora: el
usuario pide uno nuevo.

**Avisos de licencia.** A las 7:00 (Guatemala) toma los avisos `PENDIENTE` con fecha de hoy o anterior. Si el chofer
está inactivo o su fecha de vencimiento cambió, lo cancela; si no, encola los correos y lo marca `ENVIADO`.

## Publicar un evento a mano

Desde la consola de RabbitMQ (<http://localhost:15672>, `guest` / `guest`): *Exchanges* → `transporte.eventos` →
*Publish message*, con *Properties* `content_type = application/json`. Cambie `idEvento` en cada envío.

**1. Contacto** — routing key `identidad.usuario.creado`:

```json
{
  "idEvento": "8d2e3f40-1111-4a2b-9c3d-000000000001",
  "tipoEvento": "UsuarioCreado",
  "versionEvento": 1,
  "ocurridoEn": "2026-10-20T14:32:10Z",
  "servicioProductor": "servicio-identidad",
  "idAgregado": "00000000-0000-4000-8000-000000000006",
  "versionAgregado": 0,
  "idCorrelacion": "8d2e3f40-2222-4a2b-9c3d-000000000001",
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

**2. Correo** — routing key `cartera.cartera.recargada`:

```json
{
  "idEvento": "8d2e3f40-3333-4a2b-9c3d-000000000001",
  "tipoEvento": "CarteraRecargada",
  "versionEvento": 1,
  "ocurridoEn": "2026-10-20T14:35:00Z",
  "servicioProductor": "servicio-clientes",
  "idAgregado": "7c9e1a2b-3c4d-4e5f-9a0b-1c2d3e4f5a6b",
  "versionAgregado": 1,
  "idCorrelacion": "8d2e3f40-4444-4a2b-9c3d-000000000001",
  "datos": {
    "idMovimiento": "9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a",
    "idCartera": "7c9e1a2b-3c4d-4e5f-9a0b-1c2d3e4f5a6b",
    "idUsuario": "00000000-0000-4000-8000-000000000006",
    "monto": "200.00",
    "saldoPosterior": "350.00",
    "fechaRecarga": "2026-10-20"
  }
}
```

Compruebe en la base (`SELECT tipo, estado, asunto FROM notificacion ORDER BY creado_en DESC;`). En 30 s, o con
`POST /notificaciones/interno/procesar`, el correo aparece en Mailpit (<http://localhost:8025>). Un mensaje inválido
aparece en la DLQ de su cola, por ejemplo `notificaciones.cartera.dlq`.

## Despliegue en Cloud Run

Este servicio trabaja en segundo plano: consume colas y corre dos jobs. Debe desplegarse con
**`--min-instances=1` y `--no-cpu-throttling`** (CPU siempre asignada); si no, Cloud Run lo duerme o le quita la CPU
fuera de las peticiones y deja de consumir eventos y de enviar correos. Como respaldo, Cloud Scheduler puede llamar
`POST /notificaciones/interno/procesar` con un token de ADMIN_SISTEMA. El `Dockerfile` es el mismo de los demás
servicios. Use SMTP en el puerto **587** con STARTTLS (el 25 está bloqueado).

## Pruebas

```bash
./mvnw test     # unitarias (*Test) + JaCoCo ≥ 85 %
./mvnw verify   # + integración (*IT, Testcontainers: requiere Docker) + JaCoCo de integración ≥ 60 %
```

- **Unitarias** (JUnit 5 + Mockito, `@Nested` por método, fixtures en `fixture/`): cálculo de fechas de los avisos
  (incluidos los que ya pasaron), espera exponencial, armado de enlaces, render real de las 11 plantillas, adaptador
  SMTP, servicios, manejadores, jobs, controladores, handler de errores, `Specification` y utilidades.
- **Integración** con PostgreSQL 16, RabbitMQ y GreenMail como SMTP (`integracion/`):
  - contactos: alta, versión vieja ignorada y evento duplicado (un solo correo);
  - licencias: tres avisos (o menos), cambio de fecha, chofer desactivado, job a chofer y admins activos, y la
    consulta como ADMIN_SUCURSAL (otra sucursal → 403);
  - correos: venta sin comprador sin correo, recarga con saldo posterior, alquiler solicitado a los admins;
  - errores: usuario sin contacto (reintentos y DLQ) y JSON inválido (DLQ);
  - enlace de contraseña: enlace correcto, cuerpo no guardado, sin duplicado, y reintento → 409;
  - SMTP caído: espera de 1, 2, 4 y 8 min, `FALLIDA`, reintento desde la bitácora y envío al volver el SMTP.
- Reportes: `target/site/jacoco/index.html` (unitarias) y `target/site/jacoco-it/index.html` (integración).

## Postman

`../../docs/postman/notificaciones.postman_collection.json` usa `{{baseUrl}}`, `{{token}}` (ADMIN_SISTEMA),
`{{tokenSucursal}}` e `{{idSucursal}}`. *Bitácora* guarda la primera notificación en `{{idNotificacion}}`.

## Manual técnico

`../../docs/manual-tecnico-notificaciones.md`: componentes, flujo del correo, envío del enlace, avisos de licencia y
despliegue.
