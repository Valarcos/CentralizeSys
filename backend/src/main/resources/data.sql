-- 1. UBICACIONES
INSERT OR IGNORE INTO ubicaciones (id, nombre) VALUES
    (1, 'Salón Principal'), (2, 'Depósito'), (3, 'Vidriera');;

-- 2. METODOS DE PAGO
INSERT OR IGNORE INTO metodos_pago (id, acronimo, descripcion) VALUES
    (1, 'E', 'Efectivo'),
    (2, 'TCM', 'Tarjeta Crédito Macro'),
    (3, 'TCS', 'Tarjeta Crédito Santander'),
    (4, 'TCG', 'Tarjeta Crédito Galicia'),
    (5, 'TBC', 'Transferencia Claudia'),
    (6, 'TBS', 'Transferencia Silvia'),
    (7, 'TBF', 'Transferencia Fico'),
    (8, 'TBMP', 'MercadoPago'),
    (9, 'TBH', 'Transferencia Habitualitá'),
(10, 'TB3', 'Transferencia a Terceros');;

-- 3. PRODUCTOS
INSERT OR IGNORE INTO productos (id, codigo, descripcion, precio_costo, precio_minorista, cantidad_stock) VALUES
    (1, 'ART-MUSC-N', 'Musculosa Negra - Básica', 10000.0, 56800.0, 0),
    (2, 'ART-REM-CUE', 'Remera Cuello Desflecado', 21000.0, 69800.0, 0),
    (3, 'ART-PANT-GAB', 'Pantalón Gabardina Beige', 15000.0, 45000.0, 0),
    (4, 'ART-SWEATER', 'Sweater Lana Merino', 25000.0, 75000.0, 0),
    (5, 'ACC-CINT', 'Cinturón Cuero', 5000.0, 12500.0, 0);;

-- 4. STOCK (Assign stock using subqueries to find IDs)
INSERT OR IGNORE INTO stock_por_ubicacion (id, producto_id, ubicacion_id, cantidad) VALUES
    (1, (SELECT id FROM productos WHERE codigo='ART-MUSC-N'), (SELECT id FROM ubicaciones WHERE nombre='Salón Principal'), 10),
    (2, (SELECT id FROM productos WHERE codigo='ART-REM-CUE'), (SELECT id FROM ubicaciones WHERE nombre='Salón Principal'), 8),
    (3, (SELECT id FROM productos WHERE codigo='ART-PANT-GAB'), (SELECT id FROM ubicaciones WHERE nombre='Depósito'), 5),
    (4, (SELECT id FROM productos WHERE codigo='ART-SWEATER'), (SELECT id FROM ubicaciones WHERE nombre='Salón Principal'), 3),
    (5, (SELECT id FROM productos WHERE codigo='ACC-CINT'), (SELECT id FROM ubicaciones WHERE nombre='Vidriera'), 2);;

-- 5. SYSTEM USER & ROLES (Must exist BEFORE ventas - FK dependency)
-- REMOVE BEFORE PRODUCTION
-- Password: YakuNeveVala97
INSERT OR IGNORE INTO usuarios (id, nombre, email, password_hash, rol) VALUES (0, 'SYSTEM', 'system@localhost', 'DISABLED', 'ADMIN');;
-- TODO: Remove test password before production deployment (Sprint 8)
-- pragma: allowlist secret
-- sonar.issue.ignore.multicriteria squid:S8215
UPDATE usuarios SET rol = 'ADMIN', password_hash = '$2a$10$lXbQfCXd4RpUG9GoHWuGi.KmkxpxhT5Cx66Gr0ScTfEoL6FNMDrtu' WHERE email = 'marcosachavalmbaj@gmail.com';;

-- 6. TEST USER (Must exist BEFORE ventas - FK dependency)
-- REMOVE BEFORE PRODUCTION
-- Email: empleado@test.com
-- Password: password1234
-- TODO: Delete this test user before production deployment (Sprint 8)
-- pragma: allowlist secret
-- sonar.issue.ignore.multicriteria squid:S8215
INSERT OR IGNORE INTO usuarios (id, nombre, email, password_hash, rol) VALUES
    (2, 'Empleado Prueba', 'empleado@test.com', '$2a$10$1Hbj4W.yzq4r5JjdmAfviO3lPFP8L.P86zoWvMM5bZpCBtMrt7ECy', 'EMPLEADO');;

-- 7. HISTORIAL DE VENTA (Now safe: usuarios exist)
INSERT OR IGNORE INTO ventas (id, fecha, cliente_nombre, total_venta, usuario_id) VALUES
    (1, '2023-10-01', 'Ingrid Peña', 56800.0, (SELECT id FROM usuarios WHERE email='admin@centralizesys.com')),
    (2, '2023-10-02', 'Maria Gonzalez', 69800.0, (SELECT id FROM usuarios WHERE email='admin@centralizesys.com'));;

-- ALTER TABLE ventas ADD COLUMN descuento_global REAL DEFAULT 0;
-- ALTER TABLE ventas ADD COLUMN tipo_venta TEXT NOT NULL DEFAULT 'MINORISTA';
--
--
-- CREATE TABLE IF NOT EXISTS pagos_deuda (
--     id INTEGER PRIMARY KEY AUTOINCREMENT,
--     deuda_id INTEGER NOT NULL,
--     metodo_pago_id INTEGER NOT NULL,
--     monto REAL NOT NULL,
--     fecha_pago TEXT NOT NULL, -- YYYY-MM-DD
--     observaciones TEXT,
--     usuario_id INTEGER, -- Auditoria de quien cobró
--     FOREIGN KEY (deuda_id) REFERENCES deudores(id),
--     FOREIGN KEY (metodo_pago_id) REFERENCES metodos_pago(id),
--     FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
--

-- SAFE Dummy Data Generation Script
-- Usage: sqlite3 data/centralizesys.db < backend/scripts/dummy_data.sql

-- Uses INSERT OR IGNORE to prevent Unique Constraint Crashes if run multiple times.
-- IDs start at 100 to avoid conflict with default data (IDs 1-5).

BEGIN TRANSACTION;

INSERT OR IGNORE INTO productos (id, codigo, descripcion, precio_costo, precio_minorista, cantidad_stock) VALUES
(100, 'DUMMY-1', 'Producto Dummy 1 - Descripcion Generica', 101.0, 201.0, 100),
(101, 'DUMMY-2', 'Producto Dummy 2 - Descripcion Generica', 102.0, 202.0, 100),
(102, 'DUMMY-3', 'Producto Dummy 3 - Descripcion Generica', 103.0, 203.0, 100),
(103, 'DUMMY-4', 'Producto Dummy 4 - Descripcion Generica', 104.0, 204.0, 100),
(104, 'DUMMY-5', 'Producto Dummy 5 - Descripcion Generica', 105.0, 205.0, 100),
(105, 'DUMMY-6', 'Producto Dummy 6 - Descripcion Generica', 106.0, 206.0, 100),
(106, 'DUMMY-7', 'Producto Dummy 7 - Descripcion Generica', 107.0, 207.0, 100),
(107, 'DUMMY-8', 'Producto Dummy 8 - Descripcion Generica', 108.0, 208.0, 100),
(108, 'DUMMY-9', 'Producto Dummy 9 - Descripcion Generica', 109.0, 209.0, 100),
(109, 'DUMMY-10', 'Producto Dummy 10 - Descripcion Generica', 110.0, 210.0, 100),
(110, 'DUMMY-11', 'Producto Dummy 11 - Descripcion Generica', 111.0, 211.0, 100),
(111, 'DUMMY-12', 'Producto Dummy 12 - Descripcion Generica', 112.0, 212.0, 100),
(112, 'DUMMY-13', 'Producto Dummy 13 - Descripcion Generica', 113.0, 213.0, 100),
(113, 'DUMMY-14', 'Producto Dummy 14 - Descripcion Generica', 114.0, 214.0, 100),
(114, 'DUMMY-15', 'Producto Dummy 15 - Descripcion Generica', 115.0, 215.0, 100),
(115, 'DUMMY-16', 'Producto Dummy 16 - Descripcion Generica', 116.0, 216.0, 100),
(116, 'DUMMY-17', 'Producto Dummy 17 - Descripcion Generica', 117.0, 217.0, 100),
(117, 'DUMMY-18', 'Producto Dummy 18 - Descripcion Generica', 118.0, 218.0, 100),
(118, 'DUMMY-19', 'Producto Dummy 19 - Descripcion Generica', 119.0, 219.0, 100),
(119, 'DUMMY-20', 'Producto Dummy 20 - Descripcion Generica', 120.0, 220.0, 100);

-- Add Stock for these items so they show up in inventory logic (Optional but good for testing)
-- Assumes 'Salón Principal' has ID 1 (based on data.sql)

INSERT OR IGNORE INTO stock_por_ubicacion (producto_id, ubicacion_id, cantidad)
SELECT id, 1, 50 FROM productos WHERE id >= 100 AND id <= 119;

COMMIT;
