# Datos semilla (V2 de cada microservicio)

Fuente de verdad de los datos iniciales. Cada servicio trae en su `V2` **sus propios datos y las proyecciones
que necesita** (`*_ref`, `rep_*`, contactos, perfiles…), con los **mismos IDs y la misma versión (0)** que el
productor. Así todo queda sincronizado al arrancar, sin presionar ningún botón.

- Los endpoints `POST …/admin/republicar-eventos` siguen disponibles para datos creados a mano o para recuperar una
  proyección. Republicar los datos semilla no cambia nada: llegan con la misma versión y los consumidores los ignoran.
- La primera edición real de cualquier registro sube su versión a 1 y sí se propaga por eventos.
- Las fechas son relativas al día en que se aplica la migración (`CURRENT_DATE ± n`, hora de Guatemala), para que
  los viajes "futuros" sigan siéndolo. Todas las bases aplican sus migraciones el mismo día, así que coinciden.
- Si cambias un dato aquí, cámbialo en **todos** los servicios de la columna "Dónde está".

## Contraseñas

| Rol | Contraseña |
|---|---|
| ADMIN_SISTEMA y ADMIN_SUCURSAL | `Admin1234` |
| CAJERO | `Cajero1234` |
| CHOFER | `Chofer1234` |
| CLIENTE | `Cliente1234` |

## Sucursales

| ID | Nombre | Departamento |
|---|---|---|
| `10000000-0000-4000-8000-000000000001` | Quetzaltenango | Quetzaltenango |
| `10000000-0000-4000-8000-000000000002` | Ciudad de Guatemala | Guatemala |
| `10000000-0000-4000-8000-000000000003` | Huehuetenango | Huehuetenango |

Dónde está: Identidad (`sucursal`), Flota, Viajes, Alquileres y Costos (`sucursal_ref`), Reportes (`rep_sucursal`).

## Usuarios

Todos los usuarios son clientes: cada uno tiene perfil y cartera en Clientes y Cartera.

| ID (`00000000-0000-4000-8000-…`) | Correo | Nombre | Rol | Sucursal | NIT · saldo |
|---|---|---|---|---|---|
| `…000000000001` | admin@transportes.gt | Administrador del Sistema | ADMIN_SISTEMA | — | — |
| `…000000000011` | admin.xela@transportes.gt | María Fernanda Pérez | ADMIN_SUCURSAL | Xela | — |
| `…000000000012` | admin.capital@transportes.gt | José Antonio Morales | ADMIN_SUCURSAL | Capital | — |
| `…000000000013` | admin.huehue@transportes.gt | Lucía Gabriela Ramírez | ADMIN_SUCURSAL | Huehue | — |
| `…000000000021` | cajero.xela@transportes.gt | Ana López | CAJERO | Xela | — |
| `…000000000022` | cajero.capital@transportes.gt | Mónica Beatriz Herrera | CAJERO | Capital | — |
| `…000000000023` | cajero.huehue@transportes.gt | Rodrigo Esteban Pú | CAJERO | Huehue | — |
| `…000000000041` | chofer101@transportes.gt | Carlos Alberto Méndez Ruiz | CHOFER | Xela | — (chofer `c…001`) |
| `…000000000042` | chofer104@transportes.gt | Alejandro Ramiro Gómez | CHOFER | Capital | — (chofer `c…004`) |
| `…000000000043` | chofer107@transportes.gt | Sandra Victoria Aguilar | CHOFER | Huehue | — (chofer `c…007`) |
| `…000000000044` | chofer102@transportes.gt | María Elena Sajquiy de López | CHOFER | Xela | — (chofer `c…002`) |
| `…000000000045` | chofer103@transportes.gt | José Francisco Cabrera | CHOFER | Xela | — (chofer `c…003`) |
| `…000000000046` | chofer105@transportes.gt | María Fernanda Ríos | CHOFER | Capital | — (chofer `c…005`) |
| `…000000000047` | chofer106@transportes.gt | Pedro Juan Chocó | CHOFER | Capital | — (chofer `c…006`) |
| `…000000000048` | chofer108@transportes.gt | Marcos Antonio Telet | CHOFER | Huehue | — (chofer `c…008`) |
| `…000000000049` | chofer109@transportes.gt | Luis Eduardo Sical | CHOFER | Huehue | — (chofer `c…009`) |
| `…000000000031` | cliente@transportes.gt | Carlos Méndez | CLIENTE | — | `12345678` · Q 4,830.00 |
| `…000000000032` | cliente2@transportes.gt | Andrea Sofía Castillo | CLIENTE | — | `87654321` · Q 4,915.00 |

Dónde está: Identidad (`usuario`), Clientes y Cartera (`cliente`, `cartera`), Boletos y Alquileres (`cliente_ref`),
Notificaciones (`contacto`). Los perfiles de cliente usan `cc000000-…-<sufijo del usuario>` y las carteras
`ca000000-…-<sufijo>`.

## Buses y choferes (Flota)

| Bus | Placa | Sucursal | Capacidad | Estado | Km |
|---|---|---|---|---|---|
| `b…001` | P900ABC | Xela | 45 | DISPONIBLE | 123,655.5 (tras el viaje `v…002`) |
| `b…002` | P901ABC | Xela | 52 | DISPONIBLE | 84,300.0 |
| `b…003` | P902ABC | Xela | 40 | DISPONIBLE | 156,800.2 |
| `b…004` | P903ABC | Capital | 50 | DISPONIBLE | 52,100.7 |
| `b…005` | P904ABC | Capital | 48 | DISPONIBLE | 98,750.1 |
| `b…006` | P905ABC | Capital | 36 | FUERA_DE_SERVICIO, inactivo | 189,400.9 |
| `b…007` | P906ABC | Huehue | 30 | DISPONIBLE | 76,540.3 (tras el viaje `v…005`) |
| `b…008` | P907ABC | Huehue | 25 | EN_TALLER (gasto `g…001`) | 45,210.6 |
| `b…009` | P908ABC | Huehue | 32 | DISPONIBLE | 210,350.4 |

Choferes `c…001`–`c…009`, tres por sucursal (los de Flota V2). `c…001` vence en 12 días y `c…005` en 26, para
los avisos de licencia; `c…009` está inactivo. Los nueve tienen usuario CHOFER (`…041`–`…049`), con el mismo nombre y correo.

Dónde está: Flota (`bus`, `chofer`), Viajes (`bus_ref`, `chofer_ref`), Costos (`bus_ref`), Notificaciones
(`chofer_licencia` y sus recordatorios), Reportes (`rep_bus`, `rep_chofer`).

## Rutas y viajes (Viajes)

| Ruta | Origen → destino | Km | Boleto |
|---|---|---|---|
| `40000000-…-001` | Xela → Capital | 205.50 | Q 85.00 |
| `40000000-…-002` | Xela → Huehue | 120.00 | Q 55.00 |
| `40000000-…-003` | Capital → Xela | 205.50 | Q 85.00 |
| `40000000-…-004` | Huehue → Xela | 120.00 | Q 55.00 |

| Viaje | Tipo | Estado | Ruta / alquiler | Salida (Guatemala) | Bus · chofer |
|---|---|---|---|---|---|
| `50000000-…-001` | REGULAR | PROGRAMADO | ruta 001 | hoy + 7, 08:00 | b001 · c001 |
| `50000000-…-002` | REGULAR | FINALIZADO | ruta 001 | hoy − 10, 06:00 | b001 · c001 |
| `50000000-…-003` | ALQUILER_PRIVADO | PENDIENTE_ASIGNACION | alquiler `c1…003` | hoy + 5, 09:00 | — |
| `50000000-…-004` | REGULAR | PROGRAMADO | ruta 003 | hoy + 3, 07:00 | b004 · c004 |
| `50000000-…-005` | ALQUILER_PRIVADO | FINALIZADO | alquiler `c1…004` | hoy − 10, 07:00 | b007 · c007 |
| `50000000-…-006` | REGULAR | PROGRAMADO | ruta 004 | hoy + 5, 06:30 | b009 · c008 |

Llegadas registradas: `v…002` recorrió 205.5 km con Q 420.00 de combustible; `v…005` recorrió 118.0 km con
Q 260.00.

Dónde está: Viajes (`ruta`, `viaje`, registros), Boletos (`viaje_ref` de los regulares), Alquileres (`id_viaje`
de sus alquileres), Reportes (`rep_ruta`, `rep_viaje`).

## Compras de boletos (Boletos)

| Compra | Canal | Viaje | Comprador | Asientos | Total | Pago |
|---|---|---|---|---|---|---|
| `d0000000-…-001` | EN_LINEA | v001 | cliente 031 | 5, 6 | Q 170.00 | cartera `e…004`, hoy − 2 |
| `d0000000-…-002` | EN_LINEA | v004 | cliente 032 | 10 | Q 85.00 | cartera `e…012`, hoy − 1 |
| `d0000000-…-003` | MOSTRADOR | v002 | NIT 87654321 (cliente 032) | 1, 2, 3 | Q 255.00 | efectivo, hoy − 11 (cajero Xela) |
| `d0000000-…-004` | MOSTRADOR | v002 | CF | 10, 11 | Q 170.00 | efectivo, hoy − 11 (admin Xela) |

## Alquileres (Alquileres)

| Alquiler | Canal | Estado | Cliente | Trayecto | Precio | Pago / viaje |
|---|---|---|---|---|---|---|
| `c1000000-…-001` | EN_LINEA | SOLICITADO | 031 | Xela → Retalhuleu | est. Q 825.00 | — |
| `c1000000-…-002` | EN_LINEA | COTIZADO | 032 | Capital → Antigua | final Q 1,200.00 | — |
| `c1000000-…-003` | MOSTRADOR | CONFIRMADO | CF | Xela → San Marcos | final Q 700.00 | efectivo hoy · viaje v003 |
| `c1000000-…-004` | MOSTRADOR | FINALIZADO | NIT 12345678 (cliente 031) | Huehue → Sololá | final Q 1,600.00 | efectivo hoy − 11 · viaje v005 |
| `c1000000-…-005` | EN_LINEA | CANCELADO | 031 | Capital → Escuintla | final Q 950.00 | cartera `e…002` y reembolso `e…003` |

## Cartera (Clientes y Cartera)

| Movimiento | Cliente | Tipo | Monto | Saldo después | Referencia | Fecha |
|---|---|---|---|---|---|---|
| `e0000000-…-001` | 031 | RECARGA | 5,000.00 | 5,000.00 | — | hoy − 30 |
| `e0000000-…-002` | 031 | PAGO | 950.00 | 4,050.00 | ALQUILER c1…005 | hoy − 23 |
| `e0000000-…-003` | 031 | REEMBOLSO | 950.00 | 5,000.00 | ALQUILER c1…005 (viaje eliminado hoy − 22) | hoy − 22 |
| `e0000000-…-004` | 031 | PAGO | 170.00 | 4,830.00 | BOLETOS d…001 | hoy − 2 |
| `e0000000-…-011` | 032 | RECARGA | 5,000.00 | 5,000.00 | — | hoy − 15 |
| `e0000000-…-012` | 032 | PAGO | 85.00 | 4,915.00 | BOLETOS d…002 | hoy − 1 |

## Costos

- Tarifa de depreciación Q 2.0000/km desde 2026-01-01. Tarifa de alquiler Q 10.00/km y Q 500.00/día desde
  2026-01-01.
- Categorías `80000000-…-001` a `…-008`: Llantas, Motor, Frenos, Suspensión, Cambio de aceite, Eléctrico,
  Carrocería, Otros.
- Taller:
  - `g…001` (`81000000-…-001`): bus 008, hoy − 3, Frenos, repuesto Q 850.00 y mano de obra Q 300.00.
  - `g…002` (`81000000-…-002`): bus 002, hoy − 15, Cambio de aceite, repuesto Q 450.00 y mano de obra Q 150.00.
- Cierres de viaje:

  | Viaje | Combustible | Depreciación | Chofer |
  |---|---|---|---|
  | v002 | Q 420.00 | 205.5 km × 2 = Q 411.00 | Q 180.00 × 1.00 |
  | v005 (alquiler) | Q 260.00 | 118.0 km × 2 = Q 236.00 | Q 185.00 × 1.15 = Q 212.75 |

Dónde está: Costos, y Reportes (`rep_gasto`, `rep_ingreso`, `rep_tarifa_depreciacion`).
