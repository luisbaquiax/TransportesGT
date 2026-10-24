-- Datos iniciales de Costos (docs/datos-semilla.md): proyecciones de Identidad y Flota con sus mismos IDs y
-- versión (0), la tarifa inicial, las categorías, dos servicios de taller y los costos de los dos viajes que Viajes
-- trae finalizados (lo que Costos calcula al consumir LlegadaRegistrada).

INSERT INTO sucursal_ref (id, nombre, activa, version)
VALUES ('10000000-0000-4000-8000-000000000001', 'Quetzaltenango', TRUE, 0),
       ('10000000-0000-4000-8000-000000000002', 'Ciudad de Guatemala', TRUE, 0),
       ('10000000-0000-4000-8000-000000000003', 'Huehuetenango', TRUE, 0);

INSERT INTO bus_ref (id, id_sucursal, placa, activo, version)
VALUES ('b0000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'P900ABC', TRUE, 0),
       ('b0000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', 'P901ABC', TRUE, 0),
       ('b0000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001', 'P902ABC', TRUE, 0),
       ('b0000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000002', 'P903ABC', TRUE, 0),
       ('b0000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000002', 'P904ABC', TRUE, 0),
       ('b0000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000002', 'P905ABC', FALSE, 0),
       ('b0000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 'P906ABC', TRUE, 0),
       ('b0000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000003', 'P907ABC', TRUE, 0),
       ('b0000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000003', 'P908ABC', TRUE, 0);

-- Tarifa inicial obligatoria (sin ella, LlegadaRegistrada falla con SIN_TARIFA); la registró el administrador de sistema
INSERT INTO configuracion_depreciacion (monto_por_km, vigente_desde, creado_por)
VALUES (2.0000, DATE '2026-01-01', '00000000-0000-4000-8000-000000000001');

INSERT INTO categoria_gasto (id, nombre) VALUES
    ('80000000-0000-4000-8000-000000000001', 'Llantas'),
    ('80000000-0000-4000-8000-000000000002', 'Motor'),
    ('80000000-0000-4000-8000-000000000003', 'Frenos'),
    ('80000000-0000-4000-8000-000000000004', 'Suspensión'),
    ('80000000-0000-4000-8000-000000000005', 'Cambio de aceite'),
    ('80000000-0000-4000-8000-000000000006', 'Eléctrico'),
    ('80000000-0000-4000-8000-000000000007', 'Carrocería'),
    ('80000000-0000-4000-8000-000000000008', 'Otros');

-- Taller (inmutable; el trigger diferido exige total = suma de líneas). b…008 sigue EN_TALLER en Flota.
INSERT INTO gasto_taller (id, id_bus, id_sucursal, fecha_servicio, total, observaciones, registrado_por, creado_en)
VALUES ('81000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000008',
        '10000000-0000-4000-8000-000000000003', CURRENT_DATE - 3, 1150.00,
        'Cambio de pastillas y discos; el bus queda en taller hasta la prueba de frenado',
        '00000000-0000-4000-8000-000000000013', now() - interval '3 days'),
       ('81000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000002',
        '10000000-0000-4000-8000-000000000001', CURRENT_DATE - 15, 600.00,
        'Servicio de los 85,000 km', '00000000-0000-4000-8000-000000000011', now() - interval '15 days');

INSERT INTO gasto_taller_linea (id, id_gasto_taller, id_categoria, descripcion, tipo, monto)
VALUES ('82000000-0000-4000-8000-000000000001', '81000000-0000-4000-8000-000000000001',
        '80000000-0000-4000-8000-000000000003', 'Pastillas y discos delanteros', 'REPUESTO', 850.00),
       ('82000000-0000-4000-8000-000000000002', '81000000-0000-4000-8000-000000000001',
        '80000000-0000-4000-8000-000000000003', 'Instalación y purga del sistema', 'MANO_DE_OBRA', 300.00),
       ('82000000-0000-4000-8000-000000000003', '81000000-0000-4000-8000-000000000002',
        '80000000-0000-4000-8000-000000000005', 'Aceite sintético y filtros', 'REPUESTO', 450.00),
       ('82000000-0000-4000-8000-000000000004', '81000000-0000-4000-8000-000000000002',
        '80000000-0000-4000-8000-000000000005', 'Cambio de aceite', 'MANO_DE_OBRA', 150.00);

-- Cierres de viaje (fecha = día de llegada en Guatemala): combustible del registro de llegada,
-- depreciación = km recorridos × tarifa vigente y salario del chofer (× 1.15 en alquiler privado)
INSERT INTO gasto_combustible (id, id_viaje, id_bus, id_sucursal, monto, fecha_gasto, creado_en)
VALUES ('83000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000002',
        'b0000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 420.00,
        CURRENT_DATE - 10, now() - interval '10 days'),
       ('83000000-0000-4000-8000-000000000002', '50000000-0000-4000-8000-000000000005',
        'b0000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 260.00,
        CURRENT_DATE - 10, now() - interval '10 days');

INSERT INTO registro_depreciacion (id, id_viaje, id_bus, id_sucursal, distancia_km, monto_por_km, monto,
                                   fecha_calculo, creado_en)
VALUES ('84000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000002',
        'b0000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 205.5, 2.0000, 411.00,
        CURRENT_DATE - 10, now() - interval '10 days'),
       ('84000000-0000-4000-8000-000000000002', '50000000-0000-4000-8000-000000000005',
        'b0000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 118.0, 2.0000, 236.00,
        CURRENT_DATE - 10, now() - interval '10 days');

INSERT INTO pago_chofer (id, id_viaje, id_chofer, id_sucursal, salario_base, multiplicador, monto, fecha_pago,
                         creado_en)
VALUES ('85000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000002',
        'c0000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 180.00, 1.00, 180.00,
        CURRENT_DATE - 10, now() - interval '10 days'),
       ('85000000-0000-4000-8000-000000000002', '50000000-0000-4000-8000-000000000005',
        'c0000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 185.00, 1.15, 212.75,
        CURRENT_DATE - 10, now() - interval '10 days');
