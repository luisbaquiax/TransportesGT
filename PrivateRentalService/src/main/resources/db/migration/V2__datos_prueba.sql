-- Datos de prueba de Alquileres (plan §2.9, docs/datos-semilla.md): proyecciones de Identidad y Cartera con sus
-- mismos IDs y versión (0), la tarifa inicial y un alquiler en cada estado del ciclo de vida.

INSERT INTO sucursal_ref (id, nombre, activa, version)
VALUES ('10000000-0000-4000-8000-000000000001', 'Quetzaltenango', TRUE, 0),
       ('10000000-0000-4000-8000-000000000002', 'Ciudad de Guatemala', TRUE, 0),
       ('10000000-0000-4000-8000-000000000003', 'Huehuetenango', TRUE, 0);

-- Perfiles comerciales (PerfilClienteActualizado): todo usuario es cliente; solo los dos clientes tienen NIT
INSERT INTO cliente_ref (id_usuario, nit, nombre_completo, activo, version)
VALUES ('00000000-0000-4000-8000-000000000001', NULL, 'Administrador del Sistema', TRUE, 0),
       ('00000000-0000-4000-8000-000000000011', NULL, 'María Fernanda Pérez', TRUE, 0),
       ('00000000-0000-4000-8000-000000000012', NULL, 'José Antonio Morales', TRUE, 0),
       ('00000000-0000-4000-8000-000000000013', NULL, 'Lucía Gabriela Ramírez', TRUE, 0),
       ('00000000-0000-4000-8000-000000000021', NULL, 'Ana López', TRUE, 0),
       ('00000000-0000-4000-8000-000000000022', NULL, 'Mónica Beatriz Herrera', TRUE, 0),
       ('00000000-0000-4000-8000-000000000023', NULL, 'Rodrigo Esteban Pú', TRUE, 0),
       ('00000000-0000-4000-8000-000000000041', NULL, 'Carlos Alberto Méndez Ruiz', TRUE, 0),
       ('00000000-0000-4000-8000-000000000042', NULL, 'Alejandro Ramiro Gómez', TRUE, 0),
       ('00000000-0000-4000-8000-000000000043', NULL, 'Sandra Victoria Aguilar', TRUE, 0),
       ('00000000-0000-4000-8000-000000000031', '12345678', 'Carlos Méndez', TRUE, 0),
       ('00000000-0000-4000-8000-000000000032', '87654321', 'Andrea Sofía Castillo', TRUE, 0);

-- Tarifa inicial obligatoria: sin ella no se estima ningún alquiler (la registró el administrador de sistema)
INSERT INTO tarifa_alquiler (precio_por_km, precio_por_dia, vigente_desde, creado_por)
VALUES (10.00, 500.00, DATE '2026-01-01', '00000000-0000-4000-8000-000000000001');

-- Precio estimado = km × 10.00 + días × 500.00 (solo ida = 1 día)
INSERT INTO alquiler (id, canal, estado, id_usuario_cliente, nombre_cliente, nit_cliente,
                      id_sucursal_origen, origen, destino, fecha_hora_salida, fecha_hora_retorno,
                      fecha_hora_llegada_estimada, ida_y_vuelta, numero_pasajeros,
                      distancia_estimada_km, precio_estimado, precio_final, id_pago, fecha_pago,
                      id_viaje, id_bus, placa_bus, id_chofer, nombre_chofer, version_viaje,
                      motivo_cancelacion, cancelado_en, version, creado_en, actualizado_en)
VALUES
 -- SOLICITADO: en línea, esperando la cotización del administrador de Xela
 ('c1000000-0000-4000-8000-000000000001', 'EN_LINEA', 'SOLICITADO',
  '00000000-0000-4000-8000-000000000031', 'Carlos Méndez', '12345678',
  '10000000-0000-4000-8000-000000000001', 'Quetzaltenango', 'Retalhuleu',
  ((CURRENT_DATE + 7) + TIME '08:00') AT TIME ZONE 'America/Guatemala', NULL, NULL, FALSE, 20,
  32.50, 825.00, NULL, NULL, NULL,
  NULL, NULL, NULL, NULL, NULL, NULL,
  NULL, NULL, 0, now() - interval '1 hour', now() - interval '1 hour'),
 -- COTIZADO: el cliente ya puede pagarlo con su cartera
 ('c1000000-0000-4000-8000-000000000002', 'EN_LINEA', 'COTIZADO',
  '00000000-0000-4000-8000-000000000032', 'Andrea Sofía Castillo', '87654321',
  '10000000-0000-4000-8000-000000000002', 'Ciudad de Guatemala', 'Antigua Guatemala',
  ((CURRENT_DATE + 10) + TIME '07:00') AT TIME ZONE 'America/Guatemala', NULL,
  ((CURRENT_DATE + 10) + TIME '10:00') AT TIME ZONE 'America/Guatemala', FALSE, 45,
  40.00, 900.00, 1200.00, NULL, NULL,
  NULL, NULL, NULL, NULL, NULL, NULL,
  NULL, NULL, 0, now() - interval '2 days', now() - interval '1 day'),
 -- CONFIRMADO: pagado en efectivo en mostrador; Viajes creó v…003 y espera bus y chofer
 ('c1000000-0000-4000-8000-000000000003', 'MOSTRADOR', 'CONFIRMADO',
  NULL, 'Cliente mostrador', 'CF',
  '10000000-0000-4000-8000-000000000001', 'Quetzaltenango', 'San Marcos',
  ((CURRENT_DATE + 5) + TIME '09:00') AT TIME ZONE 'America/Guatemala', NULL,
  ((CURRENT_DATE + 5) + TIME '11:00') AT TIME ZONE 'America/Guatemala', FALSE, 30,
  25.00, 750.00, 700.00, NULL, CURRENT_DATE,
  '50000000-0000-4000-8000-000000000003', NULL, NULL, NULL, NULL, 0,
  NULL, NULL, 0, now() - interval '3 days', now()),
 -- FINALIZADO: mostrador con el NIT del cliente …031 (aparece en su historial); viaje v…005 cerrado
 ('c1000000-0000-4000-8000-000000000004', 'MOSTRADOR', 'FINALIZADO',
  NULL, 'Carlos Méndez', '12345678',
  '10000000-0000-4000-8000-000000000003', 'Huehuetenango', 'Sololá',
  ((CURRENT_DATE - 10) + TIME '07:00') AT TIME ZONE 'America/Guatemala', NULL,
  ((CURRENT_DATE - 10) + TIME '11:00') AT TIME ZONE 'America/Guatemala', FALSE, 25,
  115.00, 1650.00, 1600.00, NULL, CURRENT_DATE - 11,
  '50000000-0000-4000-8000-000000000005', 'b0000000-0000-4000-8000-000000000007', 'P906ABC',
  'c0000000-0000-4000-8000-000000000007', 'Sandra Victoria Aguilar', 0,
  NULL, NULL, 0, now() - interval '12 days', now() - interval '10 days'),
 -- CANCELADO: pagado con cartera (movimiento e…002); se eliminó su viaje y Cartera reembolsó (e…003)
 ('c1000000-0000-4000-8000-000000000005', 'EN_LINEA', 'CANCELADO',
  '00000000-0000-4000-8000-000000000031', 'Carlos Méndez', '12345678',
  '10000000-0000-4000-8000-000000000002', 'Ciudad de Guatemala', 'Escuintla',
  ((CURRENT_DATE - 20) + TIME '07:00') AT TIME ZONE 'America/Guatemala', NULL,
  ((CURRENT_DATE - 20) + TIME '09:00') AT TIME ZONE 'America/Guatemala', FALSE, 15,
  50.00, 1000.00, 950.00, 'e0000000-0000-4000-8000-000000000002', CURRENT_DATE - 23,
  NULL, NULL, NULL, NULL, NULL, NULL,
  'VIAJE_ELIMINADO', ((CURRENT_DATE - 22) + TIME '10:00') AT TIME ZONE 'America/Guatemala',
  0, now() - interval '25 days', now() - interval '22 days');

-- Historial de precios: estimado del sistema y ajuste del administrador de la sucursal de origen
INSERT INTO cotizacion_alquiler (id_alquiler, tipo, precio, id_usuario, comentario)
VALUES ('c1000000-0000-4000-8000-000000000001', 'ESTIMADA', 825.00, NULL, 'Estimado automático por tarifa vigente'),
       ('c1000000-0000-4000-8000-000000000002', 'ESTIMADA', 900.00, NULL, 'Estimado automático por tarifa vigente'),
       ('c1000000-0000-4000-8000-000000000002', 'AJUSTADA_ADMIN', 1200.00, '00000000-0000-4000-8000-000000000012', 'Incluye espera en Antigua'),
       ('c1000000-0000-4000-8000-000000000003', 'ESTIMADA', 750.00, NULL, 'Estimado automático por tarifa vigente'),
       ('c1000000-0000-4000-8000-000000000003', 'AJUSTADA_ADMIN', 700.00, '00000000-0000-4000-8000-000000000011', 'Descuento por recorrido habitual'),
       ('c1000000-0000-4000-8000-000000000004', 'ESTIMADA', 1650.00, NULL, 'Estimado automático por tarifa vigente'),
       ('c1000000-0000-4000-8000-000000000004', 'AJUSTADA_ADMIN', 1600.00, '00000000-0000-4000-8000-000000000013', 'Ajuste final antes de viajar'),
       ('c1000000-0000-4000-8000-000000000005', 'ESTIMADA', 1000.00, NULL, 'Estimado automático por tarifa vigente'),
       ('c1000000-0000-4000-8000-000000000005', 'AJUSTADA_ADMIN', 950.00, '00000000-0000-4000-8000-000000000012', 'Precio acordado con el cliente');
