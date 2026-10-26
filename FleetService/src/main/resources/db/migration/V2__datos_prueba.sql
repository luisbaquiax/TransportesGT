-- Datos de prueba (plan §2.9, docs/datos-semilla.md): 3 sucursales, 9 buses y 9 choferes.
-- UUID fijos y legibles: buses b000...001-009, choferes c000...001-009. Viajes, Costos, Notificaciones y Reportes
-- traen sus proyecciones de estos buses y choferes en su propia V2 (mismos IDs, versión 0), así que no se
-- siembran eventos: POST /v1/fleet/admin/republicar-eventos queda para recuperar una proyección.

-- Sucursales: las mismas de Identidad (V2__datos_iniciales.sql); ON CONFLICT por si ya llegaron por evento.
INSERT INTO sucursal_ref (id, nombre, activa, version)
VALUES ('10000000-0000-4000-8000-000000000001', 'Quetzaltenango', TRUE, 0),
       ('10000000-0000-4000-8000-000000000002', 'Ciudad de Guatemala', TRUE, 0),
       ('10000000-0000-4000-8000-000000000003', 'Huehuetenango', TRUE, 0)
ON CONFLICT (id) DO NOTHING;

-- Buses: 3 por sucursal; uno en taller y uno desactivado para probar los filtros. El kilometraje de b…001 y b…007
-- ya incluye los viajes finalizados de Viajes (v…002 y v…005).
INSERT INTO bus (id, id_sucursal, placa, marca, modelo, anio_fabricacion, capacidad_pasajeros,
                 estado_operativo, kilometraje_actual, activo, version)
VALUES ('b0000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001',
        'P900ABC', 'Toyota', 'Hiace', 2019, 45, 'DISPONIBLE', 123655.5, TRUE, 0),
       ('b0000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001',
        'P901ABC', 'Mercedes-Benz', 'O500', 2021, 52, 'DISPONIBLE', 84300.0, TRUE, 0),
       ('b0000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001',
        'P902ABC', 'Hino', '500', 2018, 40, 'DISPONIBLE', 156800.2, TRUE, 0),
       ('b0000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000002',
        'P903ABC', 'Volvo', 'B12R', 2022, 50, 'DISPONIBLE', 52100.7, TRUE, 0),
       ('b0000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000002',
        'P904ABC', 'Scania', 'K360', 2020, 48, 'DISPONIBLE', 98750.1, TRUE, 0),
       ('b0000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000002',
        'P905ABC', 'Isuzu', 'FTR', 2017, 36, 'FUERA_DE_SERVICIO', 189400.9, FALSE, 0),
       ('b0000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003',
        'P906ABC', 'Chevrolet', 'NHR', 2019, 30, 'DISPONIBLE', 76540.3, TRUE, 0),
       ('b0000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000003',
        'P907ABC', 'Hino', 'Dutro', 2021, 25, 'EN_TALLER', 45210.6, TRUE, 0),
       ('b0000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000003',
        'P908ABC', 'Nissan', 'UD', 2016, 32, 'DISPONIBLE', 210350.4, TRUE, 0);

-- Choferes: 3 por sucursal; LIC-GT-101 vence en 12 días (avisos de 30/15/3) y LIC-GT-109 está desactivada.
-- Cada chofer tiene su usuario CHOFER en Identidad (…041–…049) para registrar salida y llegada.
-- Licencias manuales: CURRENT_DATE + n, con la fecha corriente como valor inicial (enunciado).
INSERT INTO chofer (id, id_usuario, id_sucursal, nombre_completo, correo, telefono, numero_licencia, tipo_licencia,
                    fecha_vencimiento_licencia, salario_base_viaje, activo, version)
VALUES ('c0000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000041', '10000000-0000-4000-8000-000000000001',
        'Carlos Alberto Méndez Ruiz', 'chofer101@transportes.gt', '5555-1001', 'LIC-GT-101', 'A',
        CURRENT_DATE + 12, 180.00, TRUE, 0),
       ('c0000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-000000000044', '10000000-0000-4000-8000-000000000001',
        'María Elena Sajquiy de López', 'chofer102@transportes.gt', '5555-1002', 'LIC-GT-102', 'B',
        DATE '2027-08-30', 165.00, TRUE, 0),
       ('c0000000-0000-4000-8000-000000000003', '00000000-0000-4000-8000-000000000045', '10000000-0000-4000-8000-000000000001',
        'José Francisco Cabrera', 'chofer103@transportes.gt', '5555-1003', 'LIC-GT-103', 'C',
        DATE '2028-01-15', 210.00, TRUE, 0),
       ('c0000000-0000-4000-8000-000000000004', '00000000-0000-4000-8000-000000000042', '10000000-0000-4000-8000-000000000002',
        'Alejandro Ramiro Gómez', 'chofer104@transportes.gt', '5555-1004', 'LIC-GT-104', 'A',
        DATE '2027-05-20', 190.00, TRUE, 0),
       ('c0000000-0000-4000-8000-000000000005', '00000000-0000-4000-8000-000000000046', '10000000-0000-4000-8000-000000000002',
        'María Fernanda Ríos', 'chofer105@transportes.gt', '5555-1005', 'LIC-GT-105', 'B',
        CURRENT_DATE + 26, 175.00, TRUE, 0),
       ('c0000000-0000-4000-8000-000000000006', '00000000-0000-4000-8000-000000000047', '10000000-0000-4000-8000-000000000002',
        'Pedro Juan Chocó', 'chofer106@transportes.gt', '5555-1006', 'LIC-GT-106', 'E',
        DATE '2027-12-01', 230.00, TRUE, 0),
       ('c0000000-0000-4000-8000-000000000007', '00000000-0000-4000-8000-000000000043', '10000000-0000-4000-8000-000000000003',
        'Sandra Victoria Aguilar', 'chofer107@transportes.gt', '5555-1007', 'LIC-GT-107', 'A',
        DATE '2027-06-18', 185.00, TRUE, 0),
       ('c0000000-0000-4000-8000-000000000008', '00000000-0000-4000-8000-000000000048', '10000000-0000-4000-8000-000000000003',
        'Marcos Antonio Telet', 'chofer108@transportes.gt', '5555-1008', 'LIC-GT-108', 'B',
        DATE '2028-03-03', 160.00, TRUE, 0),
       ('c0000000-0000-4000-8000-000000000009', '00000000-0000-4000-8000-000000000049', '10000000-0000-4000-8000-000000000003',
        'Luis Eduardo Sical', 'chofer109@transportes.gt', '5555-1009', 'LIC-GT-109', 'M',
        DATE '2027-09-09', 145.00, FALSE, 0);
