-- Datos de prueba de Viajes: refs de sucursales/buses/choferes y viajes de ejemplo.
-- Los IDs coinciden con los del resto del monorepo (FleetService semilla).

-- Sucursales (mismas que en Identity/Fleet)
INSERT INTO sucursal_ref (id, nombre, activa, version) VALUES
 ('10000000-0000-4000-8000-000000000001', 'Quetzaltenango', TRUE, 1),
 ('10000000-0000-4000-8000-000000000002', 'Ciudad de Guatemala', TRUE, 1),
 ('10000000-0000-4000-8000-000000000003', 'Huehuetenango', TRUE, 1)
ON CONFLICT (id) DO NOTHING;

-- Buses
INSERT INTO bus_ref (id, id_sucursal, placa, capacidad_pasajeros, estado_operativo, activo, version) VALUES
 ('b0000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'P900ABC', 45, 'DISPONIBLE', TRUE, 0),
 ('b0000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', 'P901ABC', 52, 'DISPONIBLE', TRUE, 0),
 ('b0000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000002', 'P903ABC', 50, 'DISPONIBLE', TRUE, 0)
ON CONFLICT (id) DO NOTHING;

-- Choferes
INSERT INTO chofer_ref (id, id_usuario, id_sucursal, nombre_completo, salario_base_viaje,
                        fecha_vencimiento_licencia, activo, version) VALUES
 ('c0000000-0000-4000-8000-000000000001', NULL,
  '10000000-0000-4000-8000-000000000001', 'Carlos Alberto Méndez Ruiz', 180.00, CURRENT_DATE + 12, TRUE, 0),
 ('c0000000-0000-4000-8000-000000000002', NULL,
  '10000000-0000-4000-8000-000000000001', 'María Elena Sajquiy de López', 165.00, '2027-08-30', TRUE, 0),
 ('c0000000-0000-4000-8000-000000000004', NULL,
  '10000000-0000-4000-8000-000000000002', 'Alejandro Ramiro Gómez', 190.00, '2027-05-20', TRUE, 0)
ON CONFLICT (id) DO NOTHING;

-- Rutas
INSERT INTO ruta (id, id_sucursal_origen, id_sucursal_destino, distancia_km, precio_boleto, creado_por, version) VALUES
 ('40000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001',
        '10000000-0000-4000-8000-000000000002', 205.50, 85.00, '00000000-0000-4000-8000-000000000001', 0),
 ('40000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001',
        '10000000-0000-4000-8000-000000000003', 120.00, 55.00, '00000000-0000-4000-8000-000000000001', 0)
ON CONFLICT (id) DO NOTHING;

-- Viaje regular PROGRAMADO (futuro)
INSERT INTO viaje (id, tipo_viaje, estado, id_ruta, id_alquiler, id_sucursal_origen,
                   nombre_origen, nombre_destino, id_bus, id_chofer,
                   fecha_hora_salida, fecha_hora_llegada_estimada,
                   precio_boleto, numero_pasajeros, version, creado_por)
VALUES ('50000000-0000-4000-8000-000000000001', 'REGULAR', 'PROGRAMADO',
        '40000000-0000-4000-8000-000000000001', NULL, '10000000-0000-4000-8000-000000000001',
        'Quetzaltenango', 'Ciudad de Guatemala',
        'b0000000-0000-4000-8000-000000000001', 'c0000000-0000-4000-8000-000000000001',
        now() + interval '7 days', now() + interval '7 days' + interval '4 hours',
        85.00, NULL, 0, '00000000-0000-4000-8000-000000000001')
ON CONFLICT (id) DO NOTHING;

-- Viaje regular FINALIZADO (histórico, con salida y llegada)
INSERT INTO viaje (id, tipo_viaje, estado, id_ruta, id_alquiler, id_sucursal_origen,
                   nombre_origen, nombre_destino, id_bus, id_chofer,
                   fecha_hora_salida, fecha_hora_llegada_estimada,
                   precio_boleto, numero_pasajeros, version, creado_por)
VALUES ('50000000-0000-4000-8000-000000000002', 'REGULAR', 'FINALIZADO',
        '40000000-0000-4000-8000-000000000001', NULL, '10000000-0000-4000-8000-000000000001',
        'Quetzaltenango', 'Ciudad de Guatemala',
        'b0000000-0000-4000-8000-000000000001', 'c0000000-0000-4000-8000-000000000001',
        now() - interval '10 days', now() - interval '10 days' + interval '4 hours',
        85.00, NULL, 0, '00000000-0000-4000-8000-000000000001')
ON CONFLICT (id) DO NOTHING;

-- Viaje de alquiler PENDIENTE_ASIGNACION (debe coincidir con un alquiler CONFIRMADO de bd_alquileres)
INSERT INTO viaje (id, tipo_viaje, estado, id_ruta, id_alquiler, id_sucursal_origen,
                   nombre_origen, nombre_destino, id_bus, id_chofer,
                   fecha_hora_salida, fecha_hora_llegada_estimada,
                   precio_boleto, numero_pasajeros, version, creado_por)
VALUES ('50000000-0000-4000-8000-000000000003', 'ALQUILER_PRIVADO', 'PENDIENTE_ASIGNACION',
        NULL, 'c1000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001',
        'Quetzaltenango', 'San Marcos',
        NULL, NULL,
        now() + interval '5 days', now() + interval '5 days' + interval '2 hours',
        NULL, 30, 0, '00000000-0000-4000-8000-000000000001')
ON CONFLICT (id) DO NOTHING;

-- Salida y llegada del viaje finalizado (inmutables)
INSERT INTO registro_salida (id, id_viaje, id_bus, id_chofer, fecha_hora_salida_real,
                             kilometraje_inicial, registrado_por)
VALUES ('60000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000002',
        'b0000000-0000-4000-8000-000000000001', 'c0000000-0000-4000-8000-000000000001',
        now() - interval '10 days' + interval '5 minutes', 123450.0,
        '00000000-0000-4000-8000-000000000001')
ON CONFLICT (id) DO NOTHING;

INSERT INTO registro_llegada (id, id_viaje, fecha_hora_llegada_real, kilometraje_final,
                              distancia_recorrida_km, gasto_combustible_total,
                              salario_base_chofer, registrado_por)
VALUES ('70000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000002',
        now() - interval '10 days' + interval '4 hours', 123655.5,
        205.5, 420.00, 180.00, '00000000-0000-4000-8000-000000000001')
ON CONFLICT (id) DO NOTHING;
