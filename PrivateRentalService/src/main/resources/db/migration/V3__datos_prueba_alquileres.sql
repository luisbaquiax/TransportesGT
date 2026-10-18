-- Datos de prueba de Alquileres (plan §2.9): un alquiler en cada estado del ciclo de vida.
-- IDs fijos para poder referenciarlos en pruebas manuales y en la colección Postman.

-- SOLICITADO: en línea, esperando cotización del administrador.
INSERT INTO alquiler (id, canal, estado, id_usuario_cliente, nombre_cliente, nit_cliente,
                      id_sucursal_origen, origen, destino, fecha_hora_salida, fecha_hora_retorno,
                      fecha_hora_llegada_estimada, ida_y_vuelta, numero_pasajeros,
                      distancia_estimada_km, precio_estimado, precio_final, id_pago, fecha_pago,
                      motivo_cancelacion, cancelado_en, version, creado_en, actualizado_en)
VALUES ('c1000000-0000-4000-8000-000000000001', 'EN_LINEA', 'SOLICITADO',
        'a0000000-0000-4000-8000-000000000001', 'Transportes Ejemplo S.A.', '1234567-8',
        '10000000-0000-4000-8000-000000000001', 'Quetzaltenango', 'Retalhuleu',
        now() + interval '7 days', NULL, NULL, FALSE, 20,
        32.50, 825.00, NULL, NULL, NULL, NULL, NULL,
        0, now() - interval '1 hour', now() - interval '1 hour')
ON CONFLICT (id) DO NOTHING;

-- COTIZADO: administrador fijó precio final y llegada estimada; aún no se paga.
INSERT INTO alquiler (id, canal, estado, id_usuario_cliente, nombre_cliente, nit_cliente,
                      id_sucursal_origen, origen, destino, fecha_hora_salida, fecha_hora_retorno,
                      fecha_hora_llegada_estimada, ida_y_vuelta, numero_pasajeros,
                      distancia_estimada_km, precio_estimado, precio_final, id_pago, fecha_pago,
                      motivo_cancelacion, cancelado_en, version, creado_en, actualizado_en)
VALUES ('c1000000-0000-4000-8000-000000000002', 'EN_LINEA', 'COTIZADO',
        'a0000000-0000-4000-8000-000000000002', 'Consumidor Final Prueba', 'CF',
        '10000000-0000-4000-8000-000000000002', 'Ciudad de Guatemala', 'Antigua',
        now() + interval '10 days', NULL, now() + interval '10 days' + interval '3 hours', FALSE, 45,
        40.00, 900.00, 1200.00, NULL, NULL, NULL, NULL,
        0, now() - interval '2 hours', now() - interval '30 minutes')
ON CONFLICT (id) DO NOTHING;

-- CONFIRMADO: pago en efectivo en mostrador (id_pago null) o con cartera (id_pago set).
-- Aquí mostrador, para no depender de un pago de cartera real en la semilla.
INSERT INTO alquiler (id, canal, estado, id_usuario_cliente, nombre_cliente, nit_cliente,
                      id_sucursal_origen, origen, destino, fecha_hora_salida, fecha_hora_retorno,
                      fecha_hora_llegada_estimada, ida_y_vuelta, numero_pasajeros,
                      distancia_estimada_km, precio_estimado, precio_final, id_pago, fecha_pago,
                      motivo_cancelacion, cancelado_en, version, creado_en, actualizado_en)
VALUES ('c1000000-0000-4000-8000-000000000003', 'MOSTRADOR', 'CONFIRMADO',
        NULL, 'Cliente mostrador', 'CF',
        '10000000-0000-4000-8000-000000000001', 'Quetzaltenango', 'San Marcos',
        now() + interval '5 days', NULL, now() + interval '5 days' + interval '2 hours', FALSE, 30,
        25.00, 750.00, 700.00, NULL, CURRENT_DATE, NULL, NULL,
        0, now() - interval '3 days', now() - interval '1 day')
ON CONFLICT (id) DO NOTHING;

-- FINALIZADO: viaje completado (llegada registrada).
INSERT INTO alquiler (id, canal, estado, id_usuario_cliente, nombre_cliente, nit_cliente,
                      id_sucursal_origen, origen, destino, fecha_hora_salida, fecha_hora_retorno,
                      fecha_hora_llegada_estimada, ida_y_vuelta, numero_pasajeros,
                      distancia_estimada_km, precio_estimado, precio_final, id_pago, fecha_pago,
                      motivo_cancelacion, cancelado_en, version, creado_en, actualizado_en)
VALUES ('c1000000-0000-4000-8000-000000000004', 'MOSTRADOR', 'FINALIZADO',
        NULL, 'Cliente mostrador', 'CF',
        '10000000-0000-4000-8000-000000000003', 'Huehuetenango', 'Sololá',
        now() - interval '10 days', NULL, now() - interval '10 days' + interval '4 hours', FALSE, 25,
        60.00, 1100.00, 1050.00, NULL, (now() - interval '11 days')::date, NULL, NULL,
        0, now() - interval '12 days', now() - interval '10 days')
ON CONFLICT (id) DO NOTHING;

-- CANCELADO: viaje eliminado mientras el alquiler estaba confirmado.
INSERT INTO alquiler (id, canal, estado, id_usuario_cliente, nombre_cliente, nit_cliente,
                      id_sucursal_origen, origen, destino, fecha_hora_salida, fecha_hora_retorno,
                      fecha_hora_llegada_estimada, ida_y_vuelta, numero_pasajeros,
                      distancia_estimada_km, precio_estimado, precio_final, id_pago, fecha_pago,
                      motivo_cancelacion, cancelado_en, version, creado_en, actualizado_en)
VALUES ('c1000000-0000-4000-8000-000000000005', 'EN_LINEA', 'CANCELADO',
        'a0000000-0000-4000-8000-000000000001', 'Transportes Ejemplo S.A.', '1234567-8',
        '10000000-0000-4000-8000-000000000002', 'Ciudad de Guatemala', 'Escuintla',
        now() - interval '20 days', NULL, now() - interval '20 days' + interval '2 hours', FALSE, 15,
        50.00, 1000.00, 950.00, 'd1000000-0000-4000-8000-000000000001', (now() - interval '21 days')::date,
        'VIAJE_ELIMINADO', now() - interval '20 days',
        0, now() - interval '25 days', now() - interval '20 days')
ON CONFLICT (id) DO NOTHING;

-- Cotizaciones de referencia para cada alquiler de prueba.
INSERT INTO cotizacion_alquiler (id_alquiler, tipo, precio, id_usuario, comentario)
VALUES ('c1000000-0000-4000-8000-000000000001', 'ESTIMADA', 825.00, NULL, 'Estimado automático por tarifa vigente'),
       ('c1000000-0000-4000-8000-000000000002', 'ESTIMADA', 900.00, NULL, 'Estimado automático por tarifa vigente'),
       ('c1000000-0000-4000-8000-000000000002', 'AJUSTADA_ADMIN', 1200.00, 'b0000000-0000-4000-8000-000000000001', 'Ajuste por ida y vuelta y espera'),
       ('c1000000-0000-4000-8000-000000000003', 'ESTIMADA', 750.00, NULL, 'Estimado automático por tarifa vigente'),
       ('c1000000-0000-4000-8000-000000000003', 'AJUSTADA_ADMIN', 700.00, 'b0000000-0000-4000-8000-000000000001', 'Descuento por recorrido habitual'),
       ('c1000000-0000-4000-8000-000000000004', 'ESTIMADA', 1100.00, NULL, 'Estimado automático por tarifa vigente'),
       ('c1000000-0000-4000-8000-000000000004', 'AJUSTADA_ADMIN', 1050.00, 'b0000000-0000-4000-8000-000000000001', 'Ajuste final antes de viajar'),
       ('c1000000-0000-4000-8000-000000000005', 'ESTIMADA', 1000.00, NULL, 'Estimado automático por tarifa vigente'),
       ('c1000000-0000-4000-8000-000000000005', 'AJUSTADA_ADMIN', 950.00, 'b0000000-0000-4000-8000-000000000001', 'Ajuste antes de cancelación');
