-- Datos iniciales de Notificaciones (docs/datos-semilla.md): proyecciones de Identidad (contactos) y Flota
-- (licencias de los choferes) con sus mismos IDs y versión (0). No hay correos enviados en la semilla.

INSERT INTO contacto (id_usuario, correo, nombre_completo, rol, id_sucursal, activo, version)
VALUES ('00000000-0000-4000-8000-000000000001', 'admin@transportes.gt', 'Administrador del Sistema',
        'ADMIN_SISTEMA', NULL, TRUE, 0),
       ('00000000-0000-4000-8000-000000000011', 'admin.xela@transportes.gt', 'María Fernanda Pérez',
        'ADMIN_SUCURSAL', '10000000-0000-4000-8000-000000000001', TRUE, 0),
       ('00000000-0000-4000-8000-000000000012', 'admin.capital@transportes.gt', 'José Antonio Morales',
        'ADMIN_SUCURSAL', '10000000-0000-4000-8000-000000000002', TRUE, 0),
       ('00000000-0000-4000-8000-000000000013', 'admin.huehue@transportes.gt', 'Lucía Gabriela Ramírez',
        'ADMIN_SUCURSAL', '10000000-0000-4000-8000-000000000003', TRUE, 0),
       ('00000000-0000-4000-8000-000000000021', 'cajero.xela@transportes.gt', 'Ana López',
        'CAJERO', '10000000-0000-4000-8000-000000000001', TRUE, 0),
       ('00000000-0000-4000-8000-000000000022', 'cajero.capital@transportes.gt', 'Mónica Beatriz Herrera',
        'CAJERO', '10000000-0000-4000-8000-000000000002', TRUE, 0),
       ('00000000-0000-4000-8000-000000000023', 'cajero.huehue@transportes.gt', 'Rodrigo Esteban Pú',
        'CAJERO', '10000000-0000-4000-8000-000000000003', TRUE, 0),
       ('00000000-0000-4000-8000-000000000041', 'chofer101@transportes.gt', 'Carlos Alberto Méndez Ruiz',
        'CHOFER', '10000000-0000-4000-8000-000000000001', TRUE, 0),
       ('00000000-0000-4000-8000-000000000042', 'chofer104@transportes.gt', 'Alejandro Ramiro Gómez',
        'CHOFER', '10000000-0000-4000-8000-000000000002', TRUE, 0),
       ('00000000-0000-4000-8000-000000000043', 'chofer107@transportes.gt', 'Sandra Victoria Aguilar',
        'CHOFER', '10000000-0000-4000-8000-000000000003', TRUE, 0),
       ('00000000-0000-4000-8000-000000000031', 'cliente@transportes.gt', 'Carlos Méndez',
        'CLIENTE', NULL, TRUE, 0),
       ('00000000-0000-4000-8000-000000000032', 'cliente2@transportes.gt', 'Andrea Sofía Castillo',
        'CLIENTE', NULL, TRUE, 0);

INSERT INTO chofer_licencia (id_chofer, id_sucursal, nombre_completo, correo, fecha_vencimiento_licencia, activo,
                             version)
VALUES ('c0000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001',
        'Carlos Alberto Méndez Ruiz', 'chofer101@transportes.gt', CURRENT_DATE + 12, TRUE, 0),
       ('c0000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001',
        'María Elena Sajquiy de López', 'chofer102@transportes.gt', DATE '2027-08-30', TRUE, 0),
       ('c0000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001',
        'José Francisco Cabrera', 'chofer103@transportes.gt', DATE '2028-01-15', TRUE, 0),
       ('c0000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000002',
        'Alejandro Ramiro Gómez', 'chofer104@transportes.gt', DATE '2027-05-20', TRUE, 0),
       ('c0000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000002',
        'María Fernanda Ríos', 'chofer105@transportes.gt', CURRENT_DATE + 26, TRUE, 0),
       ('c0000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000002',
        'Pedro Juan Chocó', 'chofer106@transportes.gt', DATE '2027-12-01', TRUE, 0),
       ('c0000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003',
        'Sandra Victoria Aguilar', 'chofer107@transportes.gt', DATE '2027-06-18', TRUE, 0),
       ('c0000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000003',
        'Marcos Antonio Telet', 'chofer108@transportes.gt', DATE '2028-03-03', TRUE, 0),
       ('c0000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000003',
        'Luis Eduardo Sical', 'chofer109@transportes.gt', DATE '2027-09-09', FALSE, 0);

-- Avisos a 30, 15 y 3 días de cada chofer activo, como CalendarioRecordatorios al consumir ChoferRegistrado:
-- solo los que todavía no pasaron (c…001 vence en 12 días: queda el de 3; c…005 en 26: quedan 15 y 3).
INSERT INTO recordatorio_licencia (id_chofer, fecha_vencimiento, dias_anticipacion, fecha_envio_programada)
SELECT c.id_chofer, c.fecha_vencimiento_licencia, d.dias, c.fecha_vencimiento_licencia - d.dias
  FROM chofer_licencia c
 CROSS JOIN (VALUES (30::smallint), (15::smallint), (3::smallint)) AS d(dias)
 WHERE c.activo
   AND c.fecha_vencimiento_licencia - d.dias >= CURRENT_DATE;
