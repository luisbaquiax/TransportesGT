-- Datos iniciales (seed). Contraseñas documentadas en el README:
--   ADMIN_SISTEMA y ADMIN_SUCURSAL: Admin1234 · CAJERO: Cajero1234 · CLIENTE: Cliente1234
--
-- Estos INSERT NO generan eventos. Para poblar las proyecciones de los demás servicios:
--   POST /v1/identity/admin/republicar-eventos  (con un token de ADMIN_SISTEMA)
--
-- Orden: el trigger trg_admin_sucursal_creador exige que creado_por sea un ADMIN_SISTEMA activo,
-- así que primero va el administrador de sistema.

-- Administrador de sistema
INSERT INTO usuario (id, correo, hash_contrasena, nombre_completo, rol, id_sucursal, activo, creado_por,
                     contrasena_cambiada_en)
VALUES ('00000000-0000-4000-8000-000000000001', 'admin@transportes.gt',
        '$2a$10$.832O2FRpiv5BJCB5r5Ec.NoPSkFsMhw//k8ZATXtVEVMpCkhLx2i',
        'Administrador del Sistema', 'ADMIN_SISTEMA', NULL, TRUE, NULL, now());

-- Sucursales (coordenadas del parque central de cada ciudad)
INSERT INTO sucursal (id, nombre, departamento, direccion, telefono, latitud, longitud)
VALUES ('10000000-0000-4000-8000-000000000001', 'Quetzaltenango', 'Quetzaltenango',
        '4a. Calle 12-35, Zona 1, frente al Parque Centroamérica', '77651234', 14.834700, -91.518100),
       ('10000000-0000-4000-8000-000000000002', 'Ciudad de Guatemala', 'Guatemala',
        '6a. Avenida 9-15, Zona 1, cerca de la Plaza de la Constitución', '22301234', 14.642700, -90.513300),
       ('10000000-0000-4000-8000-000000000003', 'Huehuetenango', 'Huehuetenango',
        '2a. Calle 4-20, Zona 1, frente al Parque Central', '77641234', 15.319700, -91.470900);

-- Un administrador por sucursal (creados por el administrador de sistema)
INSERT INTO usuario (id, correo, hash_contrasena, nombre_completo, rol, id_sucursal, activo, creado_por,
                     contrasena_cambiada_en)
VALUES ('00000000-0000-4000-8000-000000000011', 'admin.xela@transportes.gt',
        '$2a$10$.832O2FRpiv5BJCB5r5Ec.NoPSkFsMhw//k8ZATXtVEVMpCkhLx2i',
        'María Fernanda Pérez', 'ADMIN_SUCURSAL', '10000000-0000-4000-8000-000000000001', TRUE,
        '00000000-0000-4000-8000-000000000001', now());

INSERT INTO usuario (id, correo, hash_contrasena, nombre_completo, rol, id_sucursal, activo, creado_por,
                     contrasena_cambiada_en)
VALUES ('00000000-0000-4000-8000-000000000012', 'admin.capital@transportes.gt',
        '$2a$10$.832O2FRpiv5BJCB5r5Ec.NoPSkFsMhw//k8ZATXtVEVMpCkhLx2i',
        'José Antonio Morales', 'ADMIN_SUCURSAL', '10000000-0000-4000-8000-000000000002', TRUE,
        '00000000-0000-4000-8000-000000000001', now());

INSERT INTO usuario (id, correo, hash_contrasena, nombre_completo, rol, id_sucursal, activo, creado_por,
                     contrasena_cambiada_en)
VALUES ('00000000-0000-4000-8000-000000000013', 'admin.huehue@transportes.gt',
        '$2a$10$.832O2FRpiv5BJCB5r5Ec.NoPSkFsMhw//k8ZATXtVEVMpCkhLx2i',
        'Lucía Gabriela Ramírez', 'ADMIN_SUCURSAL', '10000000-0000-4000-8000-000000000003', TRUE,
        '00000000-0000-4000-8000-000000000001', now());

-- Cajero de Quetzaltenango (creado por el administrador de esa sucursal)
INSERT INTO usuario (id, correo, hash_contrasena, nombre_completo, rol, id_sucursal, activo, creado_por,
                     contrasena_cambiada_en)
VALUES ('00000000-0000-4000-8000-000000000021', 'cajero.xela@transportes.gt',
        '$2a$10$mYeDpTUJNtQAXwxaBCQP..nN6va5gjhh5TelMFsl6do3oW9Juv3nu',
        'Ana López', 'CAJERO', '10000000-0000-4000-8000-000000000001', TRUE,
        '00000000-0000-4000-8000-000000000011', now());

-- Cliente (autorregistrado: sin creado_por)
INSERT INTO usuario (id, correo, hash_contrasena, nombre_completo, rol, id_sucursal, activo, creado_por,
                     contrasena_cambiada_en)
VALUES ('00000000-0000-4000-8000-000000000031', 'cliente@transportes.gt',
        '$2a$10$gCMxqYrl4x6hDdhkSh0j1eggBoku6B/w2zRtSBRxb4J2GWABB4haW',
        'Carlos Méndez', 'CLIENTE', NULL, TRUE, NULL, now());
