-- CURRENT DROP TABLE deletes all data in the table
-- every time I run the application
-- DROP TABLE IF EXISTS products;

-- 1. Tabla de PRODUCTOS (Inventario)
-- CAMBIO ARQUITECTÓNICO:
-- Se eliminó la restricción UNIQUE del 'codigo'.
-- Ahora se permite repetir código si el costo/precio es distinto.
CREATE TABLE IF NOT EXISTS productos (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    descripcion TEXT NOT NULL,
    codigo TEXT NOT NULL,             -- Ya no es UNIQUE por sí solo
    precio_costo REAL NOT NULL,
    precio_mayorista REAL,
    precio_minorista REAL NOT NULL,
    cantidad_stock INTEGER DEFAULT 0,
    tiendanube_id TEXT,

    -- Constraint Compuesta:
    -- Permite mismo código con distinto precio.
    -- Bloquea duplicados exactos (mismo código Y mismo precio).
    CONSTRAINT unique_producto_variante UNIQUE (codigo, precio_costo, precio_minorista)
);;

-- 2. Tabla de Ubicaciones
CREATE TABLE IF NOT EXISTS ubicaciones (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre TEXT NOT NULL UNIQUE
);;

-- 3. Usuarios (MOVED UP FOR FK DEPENDENCY)
CREATE TABLE IF NOT EXISTS usuarios (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    rol TEXT NOT NULL DEFAULT 'EMPLEADO' CHECK(rol IN ('ADMIN', 'EMPLEADO')),
    fecha_creacion TEXT DEFAULT (datetime('now', 'localtime'))
);;

-- Usuario Sistema reservado (ID convencional = 1, primer AUTOINCREMENT)
-- IMPORTANTE: Este usuario nunca debe ser eliminado. Representa al actor "Sistema" en la tabla auditoria.
-- Se usa userId=0L en el código Java para acciones automatizadas (backups, tareas programadas).
-- El campo usuario_id en auditoria es nullable para compatibilidad, pero la convención vigente es ID=0.
INSERT OR IGNORE INTO usuarios (id, nombre, email, password_hash, rol)
VALUES (0, 'Sistema', 'sistema@centralizesys.internal', 'NO_LOGIN', 'EMPLEADO');;

-- Usuario Administrador
INSERT OR IGNORE INTO usuarios (nombre, email, password_hash, rol)
VALUES ('Administrador', 'marcosachavalmbaj@gmail.com', '$2a$10$lXbQfCXd4RpUG9GoHWuGi.KmkxpxhT5Cx66Gr0ScTfEoL6FNMDrtu', 'ADMIN');;

-- 4. Métodos de Pago (MOVED UP FOR FK DEPENDENCY)
CREATE TABLE IF NOT EXISTS metodos_pago (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    acronimo TEXT NOT NULL UNIQUE,
    descripcion TEXT NOT NULL
);;

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

-- 5. Stock por Ubicación (Tabla Intermedia)
CREATE TABLE IF NOT EXISTS stock_por_ubicacion (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    producto_id INTEGER NOT NULL,
    ubicacion_id INTEGER NOT NULL,
    cantidad INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (producto_id) REFERENCES productos(id) ON DELETE CASCADE,
    FOREIGN KEY (ubicacion_id) REFERENCES ubicaciones(id),
    UNIQUE(producto_id, ubicacion_id)
);;

-- 6. Compra de mercaderia
CREATE TABLE IF NOT EXISTS compras (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    fecha TEXT NOT NULL,
    proveedor TEXT,
    nro_comprobante TEXT,
    total_compra REAL NOT NULL,
    usuario_id INTEGER,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);;

-- 7. Detalles de compra
CREATE TABLE IF NOT EXISTS detalles_compra (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    compra_id INTEGER NOT NULL,
    producto_id INTEGER NOT NULL,
    cantidad INTEGER NOT NULL,
    costo_unitario REAL NOT NULL,
    subtotal REAL NOT NULL,
    FOREIGN KEY (compra_id) REFERENCES compras(id),
    FOREIGN KEY (producto_id) REFERENCES productos(id)
);;

-- 8. Tabla de VENTAS (Cabecera)
CREATE TABLE IF NOT EXISTS ventas (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    fecha TEXT NOT NULL,
    cliente_nombre TEXT,
    total_venta REAL NOT NULL,
    descuento_global REAL DEFAULT 0, -- NEW: Global discount applied to the total
    tipo_venta TEXT,                 -- NEW: Records 'MAYORISTA' or 'MINORISTA' for history accuracy
    usuario_id INTEGER, -- Agregado para auditoría
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);;

-- 9. Tabla DETALLES_VENTA
CREATE TABLE IF NOT EXISTS detalles_venta (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    venta_id INTEGER NOT NULL,
    producto_id INTEGER,

    -- Snapshots
    descripcion_snapshot TEXT NOT NULL,
    codigo_snapshot TEXT NOT NULL,
    costo_snapshot REAL,              -- NEW: Required for legacy profit calc (since Product ID is null)

    -- Values
    cantidad INTEGER NOT NULL,
    precio_lista REAL NOT NULL,      -- NEW: The original price BEFORE discount

    -- Discount Info
    descuento_valor REAL DEFAULT 0,     -- The number input (e.g., 10 or 500)

    precio_unitario REAL NOT NULL,   -- The FINAL price after discount
    subtotal REAL NOT NULL,          -- cantidad * precio_unitario

    FOREIGN KEY (venta_id) REFERENCES ventas(id),
    FOREIGN KEY (producto_id) REFERENCES productos(id)
);;

-- 10. Tabla PAGOS_VENTA
CREATE TABLE IF NOT EXISTS pagos_venta (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    venta_id INTEGER NOT NULL,
    metodo_pago_id INTEGER NOT NULL,
    monto REAL NOT NULL,
    FOREIGN KEY (venta_id) REFERENCES ventas(id),
    FOREIGN KEY (metodo_pago_id) REFERENCES metodos_pago(id)
);;

-- 11. Tabla DEUDORES
CREATE TABLE IF NOT EXISTS deudores (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    venta_id INTEGER NOT NULL,
    cliente_nombre TEXT NOT NULL,
    monto_deuda REAL NOT NULL,
    fecha_deuda TEXT NOT NULL,
    fecha_pago TEXT,
    estado TEXT CHECK(estado IN ('PENDIENTE', 'PARCIAL', 'PAGADO')) DEFAULT 'PENDIENTE',
FOREIGN KEY (venta_id) REFERENCES ventas(id)
);;

-- 12. Tabla PAGOS_DEUDA (Historial de pagos de deudas)
CREATE TABLE IF NOT EXISTS pagos_deuda (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    deuda_id INTEGER NOT NULL,
    metodo_pago_id INTEGER NOT NULL,
    monto REAL NOT NULL,
    fecha_pago TEXT NOT NULL, -- YYYY-MM-DD
    observaciones TEXT,
    usuario_id INTEGER, -- Auditoria de quien cobró
    FOREIGN KEY (deuda_id) REFERENCES deudores(id),
    FOREIGN KEY (metodo_pago_id) REFERENCES metodos_pago(id),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);;

-- 13. Auditoria
CREATE TABLE IF NOT EXISTS auditoria (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    fecha_hora TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
    usuario_id INTEGER,
    accion TEXT NOT NULL,
    detalles TEXT,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);;

-- ÍNDICES
CREATE INDEX IF NOT EXISTS idx_ventas_fecha ON ventas(fecha);;
CREATE INDEX IF NOT EXISTS idx_productos_codigo ON productos(codigo);;
CREATE INDEX IF NOT EXISTS idx_deudores_cliente ON deudores(cliente_nombre);;
CREATE INDEX IF NOT EXISTS idx_auditoria_fecha ON auditoria(fecha_hora);;
CREATE INDEX IF NOT EXISTS idx_compras_fecha ON compras(fecha);;

-- NOTA: Eliminamos el indice UNIQUE parcial de 'codigo' ya que ahora permitimos duplicados.
-- Mantenemos solo el de TiendaNube.
CREATE UNIQUE INDEX IF NOT EXISTS idx_productos_tiendanube ON productos(tiendanube_id);;

-- TRIGGERS

-- 1. update_stock_after_insert
CREATE TRIGGER IF NOT EXISTS update_stock_after_insert
    AFTER INSERT ON stock_por_ubicacion
BEGIN
    UPDATE productos
    SET cantidad_stock = (
        SELECT IFNULL(SUM(cantidad), 0)
        FROM stock_por_ubicacion
        WHERE producto_id = NEW.producto_id
    )
    WHERE id = NEW.producto_id;
END;;

-- 2. update_stock_after_update
CREATE TRIGGER IF NOT EXISTS update_stock_after_update
    AFTER UPDATE ON stock_por_ubicacion
BEGIN
    UPDATE productos
    SET cantidad_stock = (
        SELECT IFNULL(SUM(cantidad), 0)
        FROM stock_por_ubicacion
        WHERE producto_id = NEW.producto_id
    )
    WHERE id = NEW.producto_id;
END;;

-- 3. update_stock_after_delete
CREATE TRIGGER IF NOT EXISTS update_stock_after_delete
    AFTER DELETE ON stock_por_ubicacion
BEGIN
    UPDATE productos
    SET cantidad_stock = (
        SELECT IFNULL(SUM(cantidad), 0)
        FROM stock_por_ubicacion
        WHERE producto_id = OLD.producto_id
    )
    WHERE id = OLD.producto_id;
END;;

-- 4. trg_set_fecha_pago_deudores
CREATE TRIGGER IF NOT EXISTS trg_set_fecha_pago_deudores
    AFTER UPDATE OF estado ON deudores
    FOR EACH ROW
    WHEN OLD.estado <> NEW.estado
BEGIN
    UPDATE deudores
    SET fecha_pago = CASE
         WHEN NEW.estado IN ('PARCIAL', 'PAGADO') THEN strftime('%Y-%m-%d', 'now', 'localtime')
         --ELSE NULL this line is redundant because it returns NULL automatically. Normal SQLite behavior.
        END
    WHERE id = NEW.id;
END;;

-- 5. Inmutabilidad de la tabla auditoria
-- La tabla auditoria es de solo insercion (append-only).
-- Ningun registro puede ser modificado ni eliminado una vez registrado.
CREATE TRIGGER IF NOT EXISTS trg_audit_no_update
    BEFORE UPDATE ON auditoria
BEGIN
    SELECT RAISE(ABORT, 'La tabla auditoria es inmutable: no se permiten modificaciones.');
END;;

CREATE TRIGGER IF NOT EXISTS trg_audit_no_delete
    BEFORE DELETE ON auditoria
BEGIN
    SELECT RAISE(ABORT, 'La tabla auditoria es inmutable: no se permiten eliminaciones.');
END;;