-- Datos iniciales de Boletos (docs/datos-semilla.md): proyecciones de Viajes y Cartera con sus mismos IDs y
-- versión (0), y compras de ejemplo. Los pagos en línea son los movimientos e…004 y e…012 de Cartera.

-- Viajes regulares (Viajes); mismas fechas que su V2, en hora de Guatemala
INSERT INTO viaje_ref (id, id_ruta, id_sucursal_origen, nombre_origen, nombre_destino, fecha_hora_salida,
                       fecha_hora_llegada_estimada, precio_boleto, id_bus, placa_bus, capacidad_bus, estado, version)
VALUES ('50000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001',
        '10000000-0000-4000-8000-000000000001', 'Quetzaltenango', 'Ciudad de Guatemala',
        ((CURRENT_DATE + 7) + TIME '08:00') AT TIME ZONE 'America/Guatemala',
        ((CURRENT_DATE + 7) + TIME '12:00') AT TIME ZONE 'America/Guatemala',
        85.00, 'b0000000-0000-4000-8000-000000000001', 'P900ABC', 45, 'PROGRAMADO', 0),
       ('50000000-0000-4000-8000-000000000002', '40000000-0000-4000-8000-000000000001',
        '10000000-0000-4000-8000-000000000001', 'Quetzaltenango', 'Ciudad de Guatemala',
        ((CURRENT_DATE - 10) + TIME '06:00') AT TIME ZONE 'America/Guatemala',
        ((CURRENT_DATE - 10) + TIME '10:00') AT TIME ZONE 'America/Guatemala',
        85.00, 'b0000000-0000-4000-8000-000000000001', 'P900ABC', 45, 'FINALIZADO', 0),
       ('50000000-0000-4000-8000-000000000004', '40000000-0000-4000-8000-000000000003',
        '10000000-0000-4000-8000-000000000002', 'Ciudad de Guatemala', 'Quetzaltenango',
        ((CURRENT_DATE + 3) + TIME '07:00') AT TIME ZONE 'America/Guatemala',
        ((CURRENT_DATE + 3) + TIME '11:00') AT TIME ZONE 'America/Guatemala',
        85.00, 'b0000000-0000-4000-8000-000000000004', 'P903ABC', 50, 'PROGRAMADO', 0),
       ('50000000-0000-4000-8000-000000000006', '40000000-0000-4000-8000-000000000004',
        '10000000-0000-4000-8000-000000000003', 'Huehuetenango', 'Quetzaltenango',
        ((CURRENT_DATE + 5) + TIME '06:30') AT TIME ZONE 'America/Guatemala',
        ((CURRENT_DATE + 5) + TIME '09:00') AT TIME ZONE 'America/Guatemala',
        55.00, 'b0000000-0000-4000-8000-000000000009', 'P908ABC', 32, 'PROGRAMADO', 0);

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
       ('00000000-0000-4000-8000-000000000044', NULL, 'María Elena Sajquiy de López', TRUE, 0),
       ('00000000-0000-4000-8000-000000000045', NULL, 'José Francisco Cabrera', TRUE, 0),
       ('00000000-0000-4000-8000-000000000046', NULL, 'María Fernanda Ríos', TRUE, 0),
       ('00000000-0000-4000-8000-000000000047', NULL, 'Pedro Juan Chocó', TRUE, 0),
       ('00000000-0000-4000-8000-000000000048', NULL, 'Marcos Antonio Telet', TRUE, 0),
       ('00000000-0000-4000-8000-000000000049', NULL, 'Luis Eduardo Sical', TRUE, 0),
       ('00000000-0000-4000-8000-000000000031', '12345678', 'Carlos Méndez', TRUE, 0),
       ('00000000-0000-4000-8000-000000000032', '87654321', 'Andrea Sofía Castillo', TRUE, 0);

-- Compras confirmadas: dos en línea (viajes futuros) y dos en mostrador (viaje finalizado). La venta de mostrador
-- con NIT 87654321 aparece en el historial de Andrea; la sucursal del ingreso es la de origen del viaje.
INSERT INTO compra (id, id_viaje, canal, estado, cantidad_boletos, precio_unitario, monto_total,
                    id_usuario_comprador, nit_comprador, nombre_comprador, id_pago, fecha_pago,
                    id_sucursal_ingreso, id_usuario_vendedor, version, creado_en, confirmado_en)
VALUES ('d0000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000001', 'EN_LINEA', 'CONFIRMADA',
        2, 85.00, 170.00, '00000000-0000-4000-8000-000000000031', '12345678', 'Carlos Méndez',
        'e0000000-0000-4000-8000-000000000004', CURRENT_DATE - 2, '10000000-0000-4000-8000-000000000001', NULL,
        1, now() - interval '2 days', now() - interval '2 days'),
       ('d0000000-0000-4000-8000-000000000002', '50000000-0000-4000-8000-000000000004', 'EN_LINEA', 'CONFIRMADA',
        1, 85.00, 85.00, '00000000-0000-4000-8000-000000000032', '87654321', 'Andrea Sofía Castillo',
        'e0000000-0000-4000-8000-000000000012', CURRENT_DATE - 1, '10000000-0000-4000-8000-000000000002', NULL,
        1, now() - interval '1 day', now() - interval '1 day'),
       ('d0000000-0000-4000-8000-000000000003', '50000000-0000-4000-8000-000000000002', 'MOSTRADOR', 'CONFIRMADA',
        3, 85.00, 255.00, NULL, '87654321', 'Andrea Sofía Castillo',
        NULL, CURRENT_DATE - 11, '10000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000021',
        0, now() - interval '11 days', now() - interval '11 days'),
       ('d0000000-0000-4000-8000-000000000004', '50000000-0000-4000-8000-000000000002', 'MOSTRADOR', 'CONFIRMADA',
        2, 85.00, 170.00, NULL, 'CF', 'Consumidor final',
        NULL, CURRENT_DATE - 11, '10000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000011',
        0, now() - interval '11 days', now() - interval '11 days');

-- Boletos (el trigger valida el asiento contra la capacidad del bus en viaje_ref)
INSERT INTO boleto (id_compra, id_viaje, numero_asiento, estado, codigo)
VALUES ('d0000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000001', 5, 'CONFIRMADO', 'BOL-7K3MPQ2A'),
       ('d0000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000001', 6, 'CONFIRMADO', 'BOL-7K3MPQ2B'),
       ('d0000000-0000-4000-8000-000000000002', '50000000-0000-4000-8000-000000000004', 10, 'CONFIRMADO', 'BOL-9HXR4TWC'),
       ('d0000000-0000-4000-8000-000000000003', '50000000-0000-4000-8000-000000000002', 1, 'CONFIRMADO', 'BOL-5NDZ8YG2'),
       ('d0000000-0000-4000-8000-000000000003', '50000000-0000-4000-8000-000000000002', 2, 'CONFIRMADO', 'BOL-5NDZ8YG3'),
       ('d0000000-0000-4000-8000-000000000003', '50000000-0000-4000-8000-000000000002', 3, 'CONFIRMADO', 'BOL-5NDZ8YG4'),
       ('d0000000-0000-4000-8000-000000000004', '50000000-0000-4000-8000-000000000002', 10, 'CONFIRMADO', 'BOL-3VJW6EUF'),
       ('d0000000-0000-4000-8000-000000000004', '50000000-0000-4000-8000-000000000002', 11, 'CONFIRMADO', 'BOL-3VJW6EUH');

-- Pasajeros del viaje finalizado (v…002): abordaron antes de la salida, registrados por la cajera de Xela
UPDATE boleto
   SET abordado_en  = ((CURRENT_DATE - 10) + TIME '05:45') AT TIME ZONE 'America/Guatemala',
       abordado_por = '00000000-0000-4000-8000-000000000021'
 WHERE id_viaje = '50000000-0000-4000-8000-000000000002';
