# Costos (OperationalCostsService)

Costos operativos de TransportesGT: tarifas de depreciación por km, catálogo de categorías de
gasto, gastos de taller inmutables y resumen de costos por viaje (combustible + depreciación +
salario del chofer). Todo el registro es **por eventos** (outbox transaccional).

| | |
|---|---|
| Puerto | `8087` |
| Ruta base | `/v1/costs` (`server.servlet.context-path`) |
| Swagger | <http://localhost:8087/v1/costs/swagger-ui.html> |
| OpenAPI | <http://localhost:8087/v1/costs/v3/api-docs> |
| Health | <http://localhost:8087/v1/costs/actuator/health> |
| Base de datos | `bd_costos` (PostgreSQL), migraciones Flyway en `src/main/resources/db/migration` |

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
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | sí | Conexión a `bd_costos` |
| `RABBITMQ_URL` | sí | `amqp://usuario:clave@host:5672` (en la nube, `amqps://`) |
| `JWT_SECRET` | sí | Secreto HS256 en **Base64** (mínimo 32 bytes decodificados), el mismo del gateway |
| `SERVER_PORT` | no | Por defecto `8087` |
| `SPRING_PROFILES_ACTIVE` | no | `dev` (por defecto) o `prod` |

## Datos de prueba (`V1__esquema_inicial.sql`)

Sembrados al arrancar:

- **Tarifa de depreciación**: 2.00 GTQ/km vigente desde 2026-01-01.
- **Categorías de gasto**: llantas, aceite y filtros, batería, frenos, mano de obra, etc.

## Modelo de datos (resumen)

| Tabla | Rol |
|---|---|
| `configuracion_depreciacion` | Tarifas de depreciación por km, versionadas por `vigente_desde` |
| `categoria_gasto` | Catálogo de categorías (inactivas no se eliminan) |
| `gasto_taller` / `gasto_taller_linea` | Gastos de taller inmutables (bloqueo UPDATE/DELETE) |
| `gasto_combustible` / `registro_depreciacion` / `pago_chofer` | Costos de viaje generados al consumir `LlegadaRegistrada` |
| `sucursal_ref` / `bus_ref` | Proyecciones de Identidad y Flota (eventos) |
| `outbox_evento` / `evento_procesado` | Outbox transaccional + consumidor idempotente |

## Endpoints

Todos requieren JWT salvo la lectura de tarifa vigente. `ADMIN_SUCURSAL` solo opera los recursos
de su propia sucursal; `ADMIN_SISTEMA` puede consultar todo.

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/depreciacion/tarifa-vigente?fecha=` | SIS, SUC | Tarifa por km vigente a una fecha (hoy por defecto) |
| GET | `/depreciacion/tarifas` | SIS | Historial completo de tarifas |
| POST | `/depreciacion/tarifas` | SIS | Cambiar tarifa. No altera lo ya calculado. Evento: `TarifaDepreciacionCambiada` |
| GET | `/categorias?activa=` | SIS, SUC | Catálogo de categorías de gasto |
| POST | `/categorias` | SIS, SUC | Crear categoría `{ nombre, descripcion }` |
| PUT | `/categorias/{id}` | SIS, SUC | Actualizar nombre y descripción |
| PATCH | `/categorias/{id}/activa` | SIS, SUC | Activar o desactivar |
| DELETE | `/categorias/{id}` | SIS, SUC | Solo sin gastos; si tiene, `409 CATEGORIA_CON_GASTOS` (usar desactivar) |
| POST | `/taller` | SUC | Registrar gasto de taller. El total se calcula de las líneas. **Inmutable.** Evento: `GastoTallerRegistrado` |
| GET | `/taller?idBus=&desde=&hasta=` | SUC, SIS | Listar gastos de taller (SUC: solo los suyos; SIS: todos) |
| GET | `/taller/{id}` | SUC, SIS | Detalle de gasto con sus líneas |
| GET | `/viajes/{idViaje}` | SUC, SIS | Resumen de costos de un viaje: combustible, depreciación y salario del chofer |

### Ejemplo: cambiar tarifa (ADMIN_SISTEMA)

```bash
curl -X POST http://localhost:8087/v1/costs/costos/depreciacion/tarifas \
  -H "Authorization: Bearer $TOKEN_SIS" \
  -H "Content-Type: application/json" \
  -d '{"montoPorKm": 2.50, "vigenteDesde": "2026-10-18"}'
```

### Ejemplo: registrar gasto de taller (ADMIN_SUCURSAL)

```bash
curl -X POST http://localhost:8087/v1/costs/costos/taller \
  -H "Authorization: Bearer $TOKEN_SUC" \
  -H "Content-Type: application/json" \
  -d '{
    "idBus": "20000000-0000-4000-8000-000000000001",
    "fechaServicio": "2026-10-18",
    "observaciones": "Cambio de llantas delanteras",
    "lineas": [
      {"idCategoria": "<uuid-llantas>", "descripcion": "Llantas Goodyear", "tipo": "REPUESTO", "monto": 1200.00},
      {"idCategoria": "<uuid-mano-obra>", "descripcion": "Mano de obra", "tipo": "MANO_DE_OBRA", "monto": 300.00}
    ]
  }'
```

## Cálculo de costos de viaje

Al consumir `LlegadaRegistrada` (idempotente por `idViaje`), se generan:

| Costo | Fórmula |
|---|---|
| Combustible | monto del evento `gastoCombustibleTotal` |
| Depreciación | `distanciaRecorridaKm × tarifa vigente en fecha de llegada` |
| Salario chofer | `salarioBaseChofer × multiplicador` (1.00 para REGULAR; 1.15 para ALQUILER_PRIVADO) |

Si no hay tarifa vigente, se descarta el evento con error `SIN_TARIFA` (422) y se reintenta.

## Mensajes consumidos y publicados

### Consumidos (from)

| Routing key | Manejador |
|---|---|
| `identidad.sucursal.creada` / `identidad.sucursal.actualizada` | Upsert en `sucursal_ref` |
| `flota.bus.registrado` / `flota.bus.actualizado` | Upsert en `bus_ref` |
| `viajes.llegada.registrada` | Genera combustible + depreciación + salario y publica 3 eventos |

### Publicados (to)

| Routing key | Evento |
|---|---|
| `costos.combustible.registrado` | `GastoCombustibleRegistrado` |
| `costos.depreciacion.calculada` | `DepreciacionCalculada` |
| `costos.pago-chofer.registrado` | `PagoChoferRegistrado` |
| `costos.taller.registrado` | `GastoTallerRegistrado` |
| `costos.depreciacion.tarifa-cambiada` | `TarifaDepreciacionCambiada` |

## Flujos (mermaid)

```mermaid
flowchart LR
  subgraph Entrada [Mensajes]
    LR[[LlegadaRegistrada]]
    BS[[BusRegistrado / BusActualizado]]
    SC[[SucursalCreada / SucursalActualizada]]
  end

  LR --> H1[ManejadorLlegadaRegistrada]
  BS --> H2[ManejadorBusRegistrado/Actualizado]
  SC --> H3[ManejadorSucursalCreada/Actualizada]

  H1 --> DB[(bd_costos)]
  H2 --> DB
  H3 --> DB

  H1 --> OB1[Outbox: combustible]
  H1 --> OB2[Outbox: depreciacion]
  H1 --> OB3[Outbox: pago chofer]
  H1 --> SVC[CambiarTarifa / RegistrarTaller] --> OB4[Outbox: taller / tarifa]

  OB1 & OB2 & OB3 & OB4 --> RMQ[[RabbitMQ]]
```

```mermaid
sequenceDiagram
  participant V as Viajes
  participant C as Costos
  participant RMQ as RabbitMQ

  V->>RMQ: LlegadaRegistrada (kafka-style routing key)
  C->>C: idempotente por idViaje
  C->>C: combustible = total, dep = km × tarifa, salario = base × mult
  C->>RMQ: outbox (3 eventos)
  RMQ-->>C: back (publicador outbox)
```

## Endpoints de operación (no del negocio)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/actuator/health` | Health check |
| GET | `/actuator/info` | Info del servicio |
| GET | `/actuator/prometheus` | Métricas (si el perfil lo habilita) |

## Notas de implementación

- **Subdominios**: la superficie de servicio se divide en `TarifaDepreciacionService`, `CategoriaGastoService`, `GastoTallerService` y `ResumenViajeService` (SRP/ISP); el controlador inyecta cada uno.
- **Dominio de costos**: `CalculadoraCostosViaje` concentra las fórmulas (combustible, depreciación, chofer) y `PoliticaPagoChofer` los multiplicadores 1.00/1.15; `ManejadorLlegadaRegistrada` solo orquesta persistencia y outbox.
- **Autorización**: las reglas por rol/sucursal viven en `PoliticaAccesoSucursal` y las delegan los servicios.
- **Idempotencia**: `ManejadorLlegadaRegistrada` verifica `existsByIdViaje` antes de generar costos.
- **Inmutabilidad**: gastos de taller y costos de viaje no se actualizan; solo se insertan.
- **Seguridad**: `ADMIN_SUCURSAL` solo ve/edita recursos de su propia sucursal (`idSucursal` del JWT).
- **Tarifas**: `guardarTarifa` usa upsert por `vigenteDesde`; el historial se conserva completo.
- **Swagger**: documentado con OpenAPI 3 (springdoc); ejemplos de error en `RespuestaError`.
- **Cobertura**: LINE 94.8%, INSTRUCTION 89.5%, BRANCH 80.9% (jacoco 0.8.15).

## Probar con Postman

Importa `postman/costos.postman_collection.json` y define las variables:

- `baseUrl` → `http://localhost:8087/v1/costs`
- `tokenAdminSistema` → JWT con rol `ADMIN_SISTEMA`
- `tokenAdminSucursal` → JWT con rol `ADMIN_SUCURSAL` y `idSucursal` válido
- `idBus` → UUID de bus de prueba (sembrado en Flota)
