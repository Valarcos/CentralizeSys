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

-- 4. STOCK (Assign stock so you can sell immediately)
INSERT OR IGNORE INTO stock_por_ubicacion (producto_id, ubicacion_id, cantidad) VALUES
(1, 1, 10), -- 10 Musculosas en Salón
(2, 1, 8),  -- 8 Remeras
(3, 2, 5),  -- 5 Pantalones en Depósito
(4, 1, 3),
(5, 3, 2);;

-- 5. HISTORIAL DE VENTA
INSERT OR IGNORE INTO ventas (fecha, cliente_nombre, total_venta, usuario_id) VALUES
('2023-10-01', 'Ingrid Peña', 56800.0, 1),
('2023-10-02', 'Maria Gonzalez', 69800.0, 1);;