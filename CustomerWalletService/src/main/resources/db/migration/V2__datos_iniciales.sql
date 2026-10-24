-- Datos iniciales de Clientes y Cartera (docs/datos-semilla.md).
-- Todo usuario de Identidad es cliente: un perfil y una cartera por cada uno (lo que crea UsuarioCreado), con
-- version_usuario = versión del usuario en Identidad (0). Solo los dos clientes tienen NIT y saldo, y sus
-- movimientos cuadran con las compras de Boletos y el alquiler cancelado de Alquileres.
-- IDs: perfil cc000000-…-<sufijo del usuario>, cartera ca000000-…-<sufijo>, movimientos e0000000-….

INSERT INTO cliente (id, id_usuario, nombre_completo, correo, nit, dpi, telefono, direccion, activo,
                     version_usuario, version)
VALUES ('cc000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000001',
        'Administrador del Sistema', 'admin@transportes.gt', NULL, NULL, NULL, NULL, TRUE, 0, 0),
       ('cc000000-0000-4000-8000-000000000011', '00000000-0000-4000-8000-000000000011',
        'María Fernanda Pérez', 'admin.xela@transportes.gt', NULL, NULL, NULL, NULL, TRUE, 0, 0),
       ('cc000000-0000-4000-8000-000000000012', '00000000-0000-4000-8000-000000000012',
        'José Antonio Morales', 'admin.capital@transportes.gt', NULL, NULL, NULL, NULL, TRUE, 0, 0),
       ('cc000000-0000-4000-8000-000000000013', '00000000-0000-4000-8000-000000000013',
        'Lucía Gabriela Ramírez', 'admin.huehue@transportes.gt', NULL, NULL, NULL, NULL, TRUE, 0, 0),
       ('cc000000-0000-4000-8000-000000000021', '00000000-0000-4000-8000-000000000021',
        'Ana López', 'cajero.xela@transportes.gt', NULL, NULL, NULL, NULL, TRUE, 0, 0),
       ('cc000000-0000-4000-8000-000000000022', '00000000-0000-4000-8000-000000000022',
        'Mónica Beatriz Herrera', 'cajero.capital@transportes.gt', NULL, NULL, NULL, NULL, TRUE, 0, 0),
       ('cc000000-0000-4000-8000-000000000023', '00000000-0000-4000-8000-000000000023',
        'Rodrigo Esteban Pú', 'cajero.huehue@transportes.gt', NULL, NULL, NULL, NULL, TRUE, 0, 0),
       ('cc000000-0000-4000-8000-000000000041', '00000000-0000-4000-8000-000000000041',
        'Carlos Alberto Méndez Ruiz', 'chofer101@transportes.gt', NULL, NULL, NULL, NULL, TRUE, 0, 0),
       ('cc000000-0000-4000-8000-000000000042', '00000000-0000-4000-8000-000000000042',
        'Alejandro Ramiro Gómez', 'chofer104@transportes.gt', NULL, NULL, NULL, NULL, TRUE, 0, 0),
       ('cc000000-0000-4000-8000-000000000043', '00000000-0000-4000-8000-000000000043',
        'Sandra Victoria Aguilar', 'chofer107@transportes.gt', NULL, NULL, NULL, NULL, TRUE, 0, 0),
       ('cc000000-0000-4000-8000-000000000031', '00000000-0000-4000-8000-000000000031',
        'Carlos Méndez', 'cliente@transportes.gt', '12345678', '2456789010101', '5512-3456',
        '7a. Avenida 3-25, Zona 1, Quetzaltenango', TRUE, 0, 0),
       ('cc000000-0000-4000-8000-000000000032', '00000000-0000-4000-8000-000000000032',
        'Andrea Sofía Castillo', 'cliente2@transportes.gt', '87654321', '3012345670101', '4478-9012',
        '12 Calle 5-40, Zona 10, Ciudad de Guatemala', TRUE, 0, 0);

-- Saldo = suma de sus movimientos (abajo)
INSERT INTO cartera (id, id_cliente, saldo, version)
VALUES ('ca000000-0000-4000-8000-000000000001', 'cc000000-0000-4000-8000-000000000001', 0.00, 0),
       ('ca000000-0000-4000-8000-000000000011', 'cc000000-0000-4000-8000-000000000011', 0.00, 0),
       ('ca000000-0000-4000-8000-000000000012', 'cc000000-0000-4000-8000-000000000012', 0.00, 0),
       ('ca000000-0000-4000-8000-000000000013', 'cc000000-0000-4000-8000-000000000013', 0.00, 0),
       ('ca000000-0000-4000-8000-000000000021', 'cc000000-0000-4000-8000-000000000021', 0.00, 0),
       ('ca000000-0000-4000-8000-000000000022', 'cc000000-0000-4000-8000-000000000022', 0.00, 0),
       ('ca000000-0000-4000-8000-000000000023', 'cc000000-0000-4000-8000-000000000023', 0.00, 0),
       ('ca000000-0000-4000-8000-000000000041', 'cc000000-0000-4000-8000-000000000041', 0.00, 0),
       ('ca000000-0000-4000-8000-000000000042', 'cc000000-0000-4000-8000-000000000042', 0.00, 0),
       ('ca000000-0000-4000-8000-000000000043', 'cc000000-0000-4000-8000-000000000043', 0.00, 0),
       ('ca000000-0000-4000-8000-000000000031', 'cc000000-0000-4000-8000-000000000031', 4830.00, 4),
       ('ca000000-0000-4000-8000-000000000032', 'cc000000-0000-4000-8000-000000000032', 4915.00, 2);

-- Los pagos usan Clave-Idempotencia = idCompra / idAlquiler, como en la saga de Boletos y Alquileres
INSERT INTO movimiento_cartera (id, id_cartera, tipo, monto, saldo_posterior, tipo_referencia, id_referencia,
                                clave_idempotencia, fecha_movimiento, descripcion, creado_en)
VALUES ('e0000000-0000-4000-8000-000000000001', 'ca000000-0000-4000-8000-000000000031', 'RECARGA', 5000.00,
        5000.00, NULL, NULL, 'semilla-recarga-031', CURRENT_DATE - 30, 'Recarga inicial',
        now() - interval '30 days'),
       ('e0000000-0000-4000-8000-000000000002', 'ca000000-0000-4000-8000-000000000031', 'PAGO', 950.00,
        4050.00, 'ALQUILER', 'c1000000-0000-4000-8000-000000000005', 'c1000000-0000-4000-8000-000000000005',
        CURRENT_DATE - 23, 'Alquiler Ciudad de Guatemala → Escuintla', now() - interval '23 days'),
       ('e0000000-0000-4000-8000-000000000003', 'ca000000-0000-4000-8000-000000000031', 'REEMBOLSO', 950.00,
        5000.00, 'ALQUILER', 'c1000000-0000-4000-8000-000000000005', NULL,
        CURRENT_DATE - 22, 'Reembolso: viaje del alquiler eliminado', now() - interval '22 days'),
       ('e0000000-0000-4000-8000-000000000004', 'ca000000-0000-4000-8000-000000000031', 'PAGO', 170.00,
        4830.00, 'BOLETOS', 'd0000000-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000001',
        CURRENT_DATE - 2, '2 boletos Quetzaltenango → Ciudad de Guatemala', now() - interval '2 days'),
       ('e0000000-0000-4000-8000-000000000011', 'ca000000-0000-4000-8000-000000000032', 'RECARGA', 5000.00,
        5000.00, NULL, NULL, 'semilla-recarga-032', CURRENT_DATE - 15, 'Recarga inicial',
        now() - interval '15 days'),
       ('e0000000-0000-4000-8000-000000000012', 'ca000000-0000-4000-8000-000000000032', 'PAGO', 85.00,
        4915.00, 'BOLETOS', 'd0000000-0000-4000-8000-000000000002', 'd0000000-0000-4000-8000-000000000002',
        CURRENT_DATE - 1, '1 boleto Ciudad de Guatemala → Quetzaltenango', now() - interval '1 day');
