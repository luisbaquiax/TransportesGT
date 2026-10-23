-- Versión del último ViajeProgramado/ViajeActualizado aplicado al alquiler: los eventos que llegan fuera de orden
-- (versión menor o igual) se ignoran para no pisar el bus o el chofer más reciente.
ALTER TABLE alquiler ADD COLUMN IF NOT EXISTS version_viaje BIGINT;

-- NIT normalizado como en Boletos y Cartera (Nit.normalizar: sin guiones ni espacios, en mayúsculas), para que el
-- historial por NIT encuentre las solicitudes de mostrador. CF se conserva.
UPDATE alquiler
   SET nit_cliente = upper(regexp_replace(nit_cliente, '[\s/-]', '', 'g'))
 WHERE nit_cliente <> 'CF';

UPDATE cliente_ref
   SET nit = upper(regexp_replace(nit, '[\s/-]', '', 'g'))
 WHERE nit IS NOT NULL AND nit <> 'CF';
