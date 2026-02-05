-- 1. UBICACIONES
INSERT OR IGNORE INTO ubicaciones (nombre) VALUES
                                               ('Salón Principal'), ('Depósito'), ('Vidriera');;

-- 2. METODOS DE PAGO
INSERT OR IGNORE INTO metodos_pago (acronimo, descripcion) VALUES
                                                               ('E', 'Efectivo'),
                                                               ('TCM', 'Tarjeta Crédito Macro'),
                                                               ('TCS', 'Tarjeta Crédito Santander'),
                                                               ('TCG', 'Tarjeta Crédito Galicia'),
                                                               ('TBC', 'Transferencia Claudia'),
                                                               ('TBS', 'Transferencia Silvia'),
                                                               ('TBF', 'Transferencia Fico'),
                                                               ('TBMP', 'MercadoPago'),
                                                               ('TBH', 'Transferencia Habitualitá'),
                                                               ('TB3', 'Transferencia a Terceros');;

-- 3. PRODUCTOS
INSERT OR IGNORE INTO productos (codigo, descripcion, precio_costo, precio_minorista, cantidad_stock) VALUES
                                                                                                          ('ART-MUSC-N', 'Musculosa Negra - Básica', 10000.0, 56800.0, 0),
                                                                                                          ('ART-REM-CUE', 'Remera Cuello Desflecado', 21000.0, 69800.0, 0),
                                                                                                          ('ART-PANT-GAB', 'Pantalón Gabardina Beige', 15000.0, 45000.0, 0),
                                                                                                          ('ART-SWEATER', 'Sweater Lana Merino', 25000.0, 75000.0, 0),
                                                                                                          ('ACC-CINT', 'Cinturón Cuero', 5000.0, 12500.0, 0);;

-- 4. STOCK (Assign stock using subqueries to find IDs)
INSERT OR IGNORE INTO stock_por_ubicacion (producto_id, ubicacion_id, cantidad) VALUES
                                                                                    ((SELECT id FROM productos WHERE codigo='ART-MUSC-N'), (SELECT id FROM ubicaciones WHERE nombre='Salón Principal'), 10),
                                                                                    ((SELECT id FROM productos WHERE codigo='ART-REM-CUE'), (SELECT id FROM ubicaciones WHERE nombre='Salón Principal'), 8),
                                                                                    ((SELECT id FROM productos WHERE codigo='ART-PANT-GAB'), (SELECT id FROM ubicaciones WHERE nombre='Depósito'), 5),
                                                                                    ((SELECT id FROM productos WHERE codigo='ART-SWEATER'), (SELECT id FROM ubicaciones WHERE nombre='Salón Principal'), 3),
                                                                                    ((SELECT id FROM productos WHERE codigo='ACC-CINT'), (SELECT id FROM ubicaciones WHERE nombre='Vidriera'), 2);;

-- 5. HISTORIAL DE VENTA
INSERT OR IGNORE INTO ventas (fecha, cliente_nombre, total_venta, usuario_id) VALUES
                                                                                  ('2023-10-01', 'Ingrid Peña', 56800.0, (SELECT id FROM usuarios WHERE email='admin@centralizesys.com')),
                                                                                  ('2023-10-02', 'Maria Gonzalez', 69800.0, (SELECT id FROM usuarios WHERE email='admin@centralizesys.com'));

-- 6. SYSTEM USER & ROLES
-- REMOVE BEFORE PRODUCTION
-- Password: YakuNeveVala97
INSERT OR IGNORE INTO usuarios (id, nombre, email, password_hash, rol) VALUES (0, 'SYSTEM', 'system@localhost', 'DISABLED', 'ADMIN');
-- TODO: Remove test password before production deployment (Sprint 8)
-- pragma: allowlist secret
-- sonar.issue.ignore.multicriteria squid:S8215
UPDATE usuarios SET rol = 'ADMIN', password_hash = '$2a$10$lXbQfCXd4RpUG9GoHWuGi.KmkxpxhT5Cx66Gr0ScTfEoL6FNMDrtu' WHERE email = 'marcosachavalmbaj@gmail.com';;

-- 7. TEST USER (EMPLEADO role for Sprint 3 testing)
-- REMOVE BEFORE PRODUCTION
-- Email: empleado@test.com
-- Password: password1234
-- TODO: Delete this test user before production deployment (Sprint 8)
-- pragma: allowlist secret
-- sonar.issue.ignore.multicriteria squid:S8215
INSERT OR IGNORE INTO usuarios (nombre, email, password_hash, rol) VALUES
    ('Empleado Prueba', 'empleado@test.com', '$2a$10$1Hbj4W.yzq4r5JjdmAfviO3lPFP8L.P86zoWvMM5bZpCBtMrt7ECy', 'EMPLEADO');;
