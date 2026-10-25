# servicio-reportes (ReportingService)

Reportes de TransportesGT. Mantiene proyecciones de lectura (`rep_*`) alimentadas **solo por eventos** de Identidad,
Flota, Viajes, Boletos, Alquileres y Costos, y sirve los 12 reportes del enunciado en JSON (y en PDF cuando se
integre JasperReports). No publica eventos ni llama a otros servicios. Usa la librería
`EventosComunesTrasnportesGt` `v2.1.1` (sobre, payloads, consumidor idempotente y topología).

| | |
|---|---|
| Puerto | `8089` |
| Ruta base | `/v1/reports` (`server.servlet.context-path`; el gateway reenvía `/v1/reports/**` sin reescribir) |
| Swagger | <http://localhost:8089/v1/reports/swagger-ui.html> |
| OpenAPI | <http://localhost:8089/v1/reports/v3/api-docs> |
| Health | <http://localhost:8089/v1/reports/actuator/health> |
| Base de datos | `bd_reportes` (PostgreSQL), Flyway `V1__esquema_inicial.sql` (modelo v4 §9) y `V2__datos_iniciales.sql` |

## Ejecutar en local

```bash
# 1. Infraestructura (PostgreSQL + RabbitMQ)
cd ../infra && docker compose up -d

# 2. Variables (archivo .env en esta carpeta; ver tabla)
# 3. Arrancar (perfil dev por defecto). Arranque Reportes ANTES que los productores (ver "Reponer eventos")
./mvnw spring-boot:run
```

### Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | sí | Conexión a `bd_reportes` |
| `RABBITMQ_URL` | sí | `amqp://usuario:clave@host:5672` (en la nube, `amqps://`) |
| `JWT_SECRET` | sí | Secreto HS256 en **Base64**, el mismo de Identidad y del gateway. Este servicio solo **valida** el token |
| `JWT_ISSUER` | no | Emisor esperado del claim `iss`. Por defecto `servicio-identidad` |
| `SERVER_PORT` | no | Por defecto `8089` |
| `SPRING_PROFILES_ACTIVE` | no | `dev` (por defecto; permite `flyway clean`) o `prod` |
| `CORS_ALLOWED_ORIGINS` | no | Lo resuelve el gateway; se conserva por compatibilidad |

## Reportes

Todos aceptan `desde` y `hasta` (`yyyy-MM-dd`, inclusivos; **sin intervalo = todos los registros**) y
`formato=json|pdf` (por omisión `json`). Filtran por **fecha de negocio**: `fecha_ingreso` (fecha de pago) en
ingresos y `fecha_gasto` (fecha de servicio, de cálculo o de pago al chofer) en gastos; nunca por `ocurridoEn`.
Excluyen ingresos anulados, viajes eliminados y rutas eliminadas (salvo donde se indica). Ruta completa:
`/v1/reports/reportes/...`.

| # | Ruta | Rol | Filtros extra | Qué muestra |
|---|---|---|---|---|
| 1 | `GET /reportes/ingresos` | SIS | `idSucursal?` | Ingresos (boletos y alquileres) por sucursal en orden alfabético: listado, subtotal por sucursal y total global |
| 2 | `GET /reportes/gastos` | SIS | `idSucursal?` | Combustible, taller (mano de obra), repuesto, depreciación y salario del chofer por sucursal; subtotal, desglose por tipo y total |
| 3 | `GET /reportes/ganancias` | SIS | `idSucursal?` | Ingresos, gastos y ganancia neta por sucursal (incluye las que están en cero) con totales |
| 4 | `GET /reportes/rutas-demandadas` | SIS | — | Rutas de mayor a menor por boletos vendidos (origen, destino, distancia, precio, viajes con ventas, ingreso). Incluye rutas eliminadas con ventas en el intervalo |
| 5 | `GET /reportes/gastos-taller-por-categoria` | SIS | `idSucursal?`, `idBus?` | Detalle y total por categoría, de mayor a menor, y gran total |
| 6 | `GET /reportes/mapa-rutas` | SIS | `idSucursal` (obligatorio) | Rutas vigentes que salen de la sucursal con coordenadas de origen y destino (Leaflet) |
| 7 | `GET /reportes/buses` | SUC, SIS | `estado?` | Placa, marca, modelo, capacidad, estado, chofer actual (viaje en tránsito o próximo programado), kilometraje, viajes realizados, activo |
| 8 | `GET /reportes/choferes` | SUC, SIS | — | Licencia, nombre, tipo, vencimiento (vencida o por vencer en 30 días), estado y viajes realizados |
| 9 | `GET /reportes/ingresos-boletos` | SUC, SIS | `idRuta?`, `idBus?` | Por viaje: ruta, salida, bus, boletos e ingreso; totales |
| 10 | `GET /reportes/ingresos-alquileres` | SUC, SIS | — | Cliente y NIT, origen, destino, fechas, bus asignado, fecha de pago y precio; total |
| 11 | `GET /reportes/depreciacion-buses` | SUC, SIS | — | Km recorridos, tarifa vigente hoy, depreciación acumulada y promedio por km; totales |
| 12 | `GET /reportes/gastos-taller-por-bus` | SUC, SIS | `idBus?` | Por bus (de mayor a menor gasto), las líneas por categoría con subtotal y total del bus; gran total |
| — | `GET /reportes/catalogos/sucursales` | SIS | — | Sucursales para los filtros |
| — | `GET /reportes/catalogos/rutas`, `/buses` | SIS, SUC | `idSucursal?` | Rutas y buses de la sucursal para los filtros |

**Regla de sucursal:** el ADMIN_SUCURSAL siempre ve la sucursal de su token (si pasa otra `idSucursal` recibe
`403 SIN_PERMISO`); el ADMIN_SISTEMA debe indicar `idSucursal` en los reportes 7 a 12 (`400` si falta).

**Respuesta JSON** (la misma estructura alimentará el PDF):

```json
{
  "titulo": "Reporte de ingresos por sucursal",
  "filtros": { "desde": "2026-10-01", "hasta": "2026-10-31", "otros": { "sucursal": "Quetzaltenango" } },
  "generadoEn": "2026-10-24T12:00:00-06:00",
  "grupos": [
    { "clave": "uuid", "titulo": "Quetzaltenango", "filas": [ { "fecha": "2026-10-20", "monto": "170.00" } ],
      "subtotales": { "monto": "1295.00", "ingresos": 4, "boletos": 7 } }
  ],
  "totales": { "monto": "2980.00", "ingresos": 6, "boletos": 8 }
}
```

- Dinero como texto con 2 decimales; fechas y horas en `America/Guatemala`.
- Reportes sin agrupación: un solo grupo sin `clave`. Taller por bus: el grupo del bus trae `subgrupos` (categorías).
- **PDF:** pendiente (JasperReports). Hoy `formato=pdf` responde `400 VALIDACION`; el frontend imprime desde el
  navegador mientras tanto.

### Errores

Formato `RespuestaError` (`timestamp`, `status`, `error`, `codigo`, `ruta`), igual que los demás servicios:
`400 VALIDACION` (desde > hasta, formato, falta `idSucursal`), `401 NO_AUTENTICADO`, `403 SIN_PERMISO` /
`ACCESO_DENEGADO`, `404 NO_ENCONTRADO` (sucursal, bus o ruta que aún no llegó por eventos).

## Eventos consumidos

Exchange `transporte.eventos`. Una cola por productor con llaves **exactas** (sin comodines), cada una con su DLQ
(`reportes.<productor>.dlq` por `transporte.eventos.dlx`). Nunca se enlaza `identidad.usuario.*`.

| Cola | Llaves | Efecto |
|---|---|---|
| `reportes.identidad.q` | `identidad.sucursal.creada`, `.actualizada` | Upsert `rep_sucursal` por versión |
| `reportes.flota.q` | `flota.bus.registrado`, `.actualizado`, `flota.chofer.registrado`, `.actualizado` | Upsert `rep_bus` (sin tocar el kilometraje) y `rep_chofer` por versión |
| `reportes.viajes.q` | `viajes.ruta.creada`, `.actualizada`, `.eliminada`, `viajes.viaje.programado`, `.actualizado`, `.eliminado`, `.salida-registrada`, `.llegada-registrada` | `rep_ruta`, `rep_viaje`; salida → bus `EN_TRANSITO`; llegada → viaje `FINALIZADO`, `kilometraje_actual = GREATEST(...)`, bus `DISPONIBLE` |
| `reportes.boletos.q` | `boletos.venta.registrada`, `boletos.venta.anulada` | `rep_ingreso` VENTA_BOLETOS; anulación sobre la fecha de pago original |
| `reportes.alquileres.q` | `alquileres.alquiler.confirmado`, `.cancelado` | `rep_ingreso` ALQUILER; cancelación = anulado |
| `reportes.costos.q` | `costos.combustible.registrado`, `costos.depreciacion.calculada`, `costos.taller.registrado`, `costos.depreciacion.tarifa-cambiada`, `costos.pago-chofer.registrado` | `rep_gasto` (una fila por línea de taller) y `rep_tarifa_depreciacion` |

- **Idempotencia:** `evento_procesado` se inserta en la misma transacción que el efecto (librería). Instantáneas:
  upsert si `versionAgregado` es mayor. Hechos: `INSERT ... ON CONFLICT DO NOTHING`.
- **Transitorios** (BD caída o `ProyeccionNoDisponibleException`: llegó la anulación antes que la venta, la salida
  antes que el viaje, etc.): 5 intentos con espera 1 s, 2 s, 4 s, 8 s (tope 16 s) y luego DLQ.
- **Mensaje venenoso** (JSON inválido o evento sin su id): DLQ de inmediato.
- 3 consumidores por cola con `prefetch=1`: mientras un mensaje espera su reintento, los demás siguen consumiendo la
  cola (por ejemplo, llega la venta que la anulación está esperando).
- `outbox_evento` existe solo porque el publicador de la librería siempre arranca; Reportes no publica.

## Reponer eventos (Reportes arrancó tarde)

RabbitMQ descarta un mensaje si al publicarlo no hay cola enlazada (plan §2.9). Si Reportes arrancó después que los
productores, sus proyecciones quedan incompletas. Opciones:

1. **Republicar instantáneas** (ADMIN_SISTEMA): `POST /v1/identity/admin/republicar-eventos` (sucursales),
   `POST /v1/fleet/admin/republicar-eventos` (buses y choferes) y `POST /v1/trips/admin/republicar-eventos` (rutas y
   viajes). Son idempotentes: las versiones ya aplicadas se ignoran.
2. **Reponer hechos** (ventas, alquileres, gastos, salidas y llegadas) dentro de las 24 h de retención del outbox: en
   la base del productor, `UPDATE outbox_evento SET publicado_en = NULL WHERE llave_ruteo LIKE 'costos.%' AND
   creado_en > now() - interval '24 hours';` y esperar al poller (o reiniciar el productor). Los duplicados no tienen
   efecto (`ON CONFLICT DO NOTHING` + `evento_procesado`).
3. Revisar las DLQ `reportes.*.dlq` en la consola de RabbitMQ (<http://localhost:15672>) y reenviar al exchange
   `transporte.eventos` con la llave original.

`V2__datos_iniciales.sql` trae una copia de los datos sembrados de los demás servicios (mismos IDs, versión 0), así
que con todo recién creado los reportes ya tienen datos.

## Pruebas

```bash
./mvnw test     # unitarias (*Test) + JaCoCo ≥ 85 %
./mvnw verify   # + integración (*IT, Testcontainers: requiere Docker) + JaCoCo de integración ≥ 60 %
```

- **Unitarias** (`@Nested` por método, fixtures por tabla en `fixture/`): proyecciones, armado de `ReporteTabla`
  (agrupación, subtotales, totales, orden alfabético), los 12 reportes, regla de sucursal, controladores, manejadores,
  handler de errores y utilidades.
- **Integración** (`integracion/`, PostgreSQL 16 + RabbitMQ): cada evento actualiza su proyección, duplicados y
  versiones viejas; BoletosAnulados antes que la venta (reintentos); llegada desordenada sin bajar el kilometraje;
  JSON inválido y evento sin id a la DLQ; transitorio que nunca se resuelve a la DLQ; los 12 reportes con datos
  conocidos (`src/test/resources/sql/reportes-datos.sql`) con y sin intervalo; anulados que no suman; regla de
  sucursal (403), rol sin acceso (403), sin token (401), `desde > hasta` (400).
- Pendiente con JasperReports: compilar los 12 `.jrxml` y las pruebas de PDF por HTTP.
- Reportes: `target/site/jacoco/index.html` y `target/site/jacoco-it/index.html`.

## Postman

`../../docs/postman/reportes.postman_collection.json` usa `{{baseUrl}}`, `{{tokenSistema}}` y `{{tokenSucursal}}`,
con cada reporte en JSON y en PDF, los catálogos y los errores típicos.

## Despliegue

`Dockerfile` igual al de los demás servicios. En Cloud Run consume colas, así que necesita
`--min-instances=1 --no-cpu-throttling`.

## Manual técnico

`../../docs/manual-tecnico-reportes.md`: tablas, componentes, cómo cada evento alimenta cada tabla, despliegue y
supuestos.
