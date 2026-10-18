-- Datos de prueba de Alquileres (plan §2.9): 3 sucursales (las mismas de Identidad/Flota)
-- y dos perfiles comerciales de cliente para los flujos de solicitud en línea.

-- Sucursales: ON CONFLICT por si ya llegaron por SucursalCreada.
INSERT INTO sucursal_ref (id, nombre, activa, version)
VALUES ('10000000-0000-4000-8000-000000000001', 'Quetzaltenango', TRUE, 0),
       ('10000000-0000-4000-8000-000000000002', 'Ciudad de Guatemala', TRUE, 0),
       ('10000000-0000-4000-8000-000000000003', 'Huehuetenango', TRUE, 0)
ON CONFLICT (id) DO NOTHING;

-- Perfiles comerciales (alimentados por PerfilClienteActualizado en producción).
INSERT INTO cliente_ref (id_usuario, nit, nombre_completo, activo, version)
VALUES ('a0000000-0000-4000-8000-000000000001', '1234567-8', 'Transportes Ejemplo S.A.', TRUE, 0),
       ('a0000000-0000-4000-8000-000000000002', 'CF', 'Consumidor Final Prueba', TRUE, 0)
ON CONFLICT (id_usuario) DO NOTHING;
