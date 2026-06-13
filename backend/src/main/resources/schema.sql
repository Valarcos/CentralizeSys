-- 1. Tabla de PRODUCTOS (Inventario)
CREATE TABLE IF NOT EXISTS productos (
    id SERIAL PRIMARY KEY,
    descripcion TEXT NOT NULL,
    codigo TEXT NOT NULL,
    precio_costo REAL NOT NULL,
    precio_mayorista REAL,
    precio_minorista REAL NOT NULL,
    cantidad_stock INTEGER DEFAULT 0,
    tiendanube_id TEXT,
    activo BOOLEAN NOT NULL DEFAULT TRUE
);;

-- 2. Tabla de Ubicaciones
CREATE TABLE IF NOT EXISTS ubicaciones (
    id SERIAL PRIMARY KEY,
    nombre TEXT NOT NULL UNIQUE
);;

-- 3. Usuarios
CREATE TABLE IF NOT EXISTS usuarios (
    id SERIAL PRIMARY KEY,
    nombre TEXT NOT NULL,
    email TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    rol TEXT NOT NULL DEFAULT 'EMPLEADO' CHECK(rol IN ('ADMIN', 'EMPLEADO')),
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    activo BOOLEAN NOT NULL DEFAULT TRUE
);;

-- Usuario Sistema reservado (ID convencional = 1, primer SERIAL, pero necesitamos ID 0)
INSERT INTO usuarios (id, nombre, email, password_hash, rol, activo)
VALUES (0, 'Sistema', 'sistema@centralizesys.internal', 'NO_LOGIN', 'EMPLEADO', TRUE)
ON CONFLICT (id) DO NOTHING;;

-- Usuario Administrador
INSERT INTO usuarios (nombre, email, password_hash, rol)
VALUES ('Administrador', 'marcosachavalmbaj@gmail.com', '$2a$10$lXbQfCXd4RpUG9GoHWuGi.KmkxpxhT5Cx66Gr0ScTfEoL6FNMDrtu', 'ADMIN')
ON CONFLICT DO NOTHING;;

-- 4. Métodos de Pago
CREATE TABLE IF NOT EXISTS metodos_pago (
    id SERIAL PRIMARY KEY,
    acronimo TEXT NOT NULL UNIQUE,
    descripcion TEXT NOT NULL
);;

INSERT INTO metodos_pago (acronimo, descripcion) VALUES
    ('E', 'Efectivo'),
    ('TCM', 'Tarjeta Crédito Macro'),
    ('TCS', 'Tarjeta Crédito Santander'),
    ('TCG', 'Tarjeta Crédito Galicia'),
    ('TBC', 'Transferencia Claudia'),
    ('TBS', 'Transferencia Silvia'),
    ('TBF', 'Transferencia Fico'),
    ('TBMP', 'MercadoPago'),
    ('TBH', 'Transferencia Habitualitá'),
    ('TB3', 'Transferencia a Terceros')
    ON CONFLICT (acronimo) DO NOTHING;;

-- 5. Stock por Ubicación
CREATE TABLE IF NOT EXISTS stock_por_ubicacion (
    id SERIAL PRIMARY KEY,
    producto_id INTEGER NOT NULL,
    ubicacion_id INTEGER NOT NULL,
    cantidad INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (producto_id) REFERENCES productos(id) ON DELETE CASCADE,
    FOREIGN KEY (ubicacion_id) REFERENCES ubicaciones(id),
    UNIQUE(producto_id, ubicacion_id)
);;

-- 6. Compra de mercaderia
CREATE TABLE IF NOT EXISTS compras (
    id SERIAL PRIMARY KEY,
    fecha TIMESTAMP NOT NULL,
    proveedor TEXT,
    nro_comprobante TEXT,
    total_compra REAL NOT NULL,
    usuario_id INTEGER,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);;

-- 7. Detalles de compra
CREATE TABLE IF NOT EXISTS detalles_compra (
    id SERIAL PRIMARY KEY,
    compra_id INTEGER NOT NULL,
    producto_id INTEGER NOT NULL,
    cantidad INTEGER NOT NULL,
    costo_unitario REAL NOT NULL,
    subtotal REAL NOT NULL,
    FOREIGN KEY (compra_id) REFERENCES compras(id),
    FOREIGN KEY (producto_id) REFERENCES productos(id)
);;

-- 8. Tabla de VENTAS
CREATE TABLE IF NOT EXISTS ventas (
    id SERIAL PRIMARY KEY,
    fecha TIMESTAMP NOT NULL,
    cliente_nombre TEXT,
    total_venta REAL NOT NULL,
    descuento_global REAL DEFAULT 0,
    tipo_venta TEXT,
    usuario_id INTEGER,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);;

-- 9. Tabla DETALLES_VENTA
CREATE TABLE IF NOT EXISTS detalles_venta (
    id SERIAL PRIMARY KEY,
    venta_id INTEGER NOT NULL,
    producto_id INTEGER,
    descripcion_snapshot TEXT NOT NULL,
    codigo_snapshot TEXT NOT NULL,
    costo_snapshot REAL,
    cantidad INTEGER NOT NULL,
    precio_lista REAL NOT NULL,
    descuento_valor REAL DEFAULT 0,
    precio_unitario REAL NOT NULL,
    subtotal REAL NOT NULL,
    FOREIGN KEY (venta_id) REFERENCES ventas(id),
    FOREIGN KEY (producto_id) REFERENCES productos(id)
);;

-- 10. Tabla PAGOS_VENTA
CREATE TABLE IF NOT EXISTS pagos_venta (
    id SERIAL PRIMARY KEY,
    venta_id INTEGER NOT NULL,
    metodo_pago_id INTEGER NOT NULL,
    monto REAL NOT NULL,
    FOREIGN KEY (venta_id) REFERENCES ventas(id),
    FOREIGN KEY (metodo_pago_id) REFERENCES metodos_pago(id)
);;

-- 11. Tabla DEUDORES
CREATE TABLE IF NOT EXISTS deudores (
    id SERIAL PRIMARY KEY,
    venta_id INTEGER NOT NULL,
    cliente_nombre TEXT NOT NULL,
    monto_deuda REAL NOT NULL,
    fecha_deuda TIMESTAMP NOT NULL,
    fecha_pago TIMESTAMP,
    estado TEXT CHECK(estado IN ('PENDIENTE', 'PARCIAL', 'PAGADO')) DEFAULT 'PENDIENTE',
    FOREIGN KEY (venta_id) REFERENCES ventas(id)
);;

-- 12. Tabla PAGOS_DEUDA
CREATE TABLE IF NOT EXISTS pagos_deuda (
    id SERIAL PRIMARY KEY,
    deuda_id INTEGER NOT NULL,
    metodo_pago_id INTEGER NOT NULL,
    monto REAL NOT NULL,
    fecha_pago TIMESTAMP NOT NULL,
    observaciones TEXT,
    usuario_id INTEGER,
    FOREIGN KEY (deuda_id) REFERENCES deudores(id),
    FOREIGN KEY (metodo_pago_id) REFERENCES metodos_pago(id),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);;

-- 13. Auditoria
CREATE TABLE IF NOT EXISTS auditoria (
    id SERIAL PRIMARY KEY,
    fecha_hora TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    usuario_id INTEGER,
    accion TEXT NOT NULL,
    detalles TEXT,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);;

-- 14. Login Attempts (Brute-Force Protection)
-- NOTE: No FK to usuarios intentionally — we must track attempts for
-- non-existent emails to prevent bypass via slight email misspellings.
-- The UNIQUE constraint on email enables the ON CONFLICT upsert in LoginAttemptRepository.
CREATE TABLE IF NOT EXISTS login_attempts (
    id            SERIAL PRIMARY KEY,
    email         TEXT NOT NULL UNIQUE,
    ip_address    TEXT,
    attempts      INTEGER NOT NULL DEFAULT 0,
    last_attempt  TIMESTAMP NOT NULL,
    blocked_until TIMESTAMP
);;

-- 15. Active Tokens (Single-Session Enforcement)
-- Stores one active JWT per user (identified by the jti claim).
-- On new login, any prior token for the user is deleted, invalidating the old session.
CREATE TABLE IF NOT EXISTS active_tokens (
    id          SERIAL PRIMARY KEY,
    usuario_id  INTEGER NOT NULL,
    jti         TEXT NOT NULL,
    expires_at  TIMESTAMP NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);;

-- ÍNDICES
CREATE INDEX IF NOT EXISTS idx_ventas_fecha ON ventas(fecha);;
CREATE INDEX IF NOT EXISTS idx_productos_codigo ON productos(codigo);;
CREATE INDEX IF NOT EXISTS idx_deudores_cliente ON deudores(cliente_nombre);;
CREATE INDEX IF NOT EXISTS idx_auditoria_fecha ON auditoria(fecha_hora);;
CREATE INDEX IF NOT EXISTS idx_compras_fecha ON compras(fecha);;
CREATE UNIQUE INDEX IF NOT EXISTS idx_productos_tiendanube ON productos(tiendanube_id);;

-- Partial Unique Indexes for Logical Deletion:
-- Replaces the former table-level UNIQUE constraint on productos.
-- Allows re-creating a product variant with the same (codigo, precio_costo, precio_minorista)
-- after its predecessor has been soft-deleted (activo = false).
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_producto_variante_activo
    ON productos (codigo, precio_costo, precio_minorista)
    WHERE activo = true;;

-- Replaces the former table-level UNIQUE constraint on usuarios.email.
-- Allows re-registering a new user with the same email after the original
-- account has been soft-deleted (activo = false).
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_usuario_email_activo
    ON usuarios (email)
    WHERE activo = true;;

-- Security: indexes for fast login-attempt lookup and token validation.
CREATE INDEX IF NOT EXISTS idx_login_attempts_email ON login_attempts(email);;
CREATE UNIQUE INDEX IF NOT EXISTS idx_active_tokens_jti ON active_tokens(jti);;
CREATE INDEX IF NOT EXISTS idx_active_tokens_usuario ON active_tokens(usuario_id);;

-- TRIGGERS

-- Function to update stock
CREATE OR REPLACE FUNCTION fn_update_stock()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        UPDATE productos
            SET cantidad_stock = (
                SELECT COALESCE(SUM(cantidad), 0)
                FROM stock_por_ubicacion
                WHERE producto_id = OLD.producto_id
            )
            WHERE id = OLD.producto_id;
            RETURN OLD;
    ELSE
        UPDATE productos
        SET cantidad_stock = (
            SELECT COALESCE(SUM(cantidad), 0)
            FROM stock_por_ubicacion
            WHERE producto_id = NEW.producto_id
        )
        WHERE id = NEW.producto_id;
        RETURN NEW;
    END IF;
END;
$$ LANGUAGE plpgsql;;

-- 1. update_stock_after_insert
DROP TRIGGER IF EXISTS update_stock_after_insert ON stock_por_ubicacion;;
CREATE TRIGGER update_stock_after_insert
    AFTER INSERT ON stock_por_ubicacion
    FOR EACH ROW
    EXECUTE FUNCTION fn_update_stock();;

-- 2. update_stock_after_update
DROP TRIGGER IF EXISTS update_stock_after_update ON stock_por_ubicacion;;
CREATE TRIGGER update_stock_after_update
    AFTER UPDATE ON stock_por_ubicacion
    FOR EACH ROW
    EXECUTE FUNCTION fn_update_stock();;

-- 3. update_stock_after_delete
DROP TRIGGER IF EXISTS update_stock_after_delete ON stock_por_ubicacion;;
CREATE TRIGGER update_stock_after_delete
    AFTER DELETE ON stock_por_ubicacion
    FOR EACH ROW
    EXECUTE FUNCTION fn_update_stock();;

-- Function for Deudores payment date
CREATE OR REPLACE FUNCTION fn_set_fecha_pago_deudores()
    RETURNS TRIGGER AS $$
BEGIN
    IF OLD.estado IS DISTINCT FROM NEW.estado THEN
        IF NEW.estado IN ('PARCIAL', 'PAGADO') THEN
            NEW.fecha_pago = CURRENT_TIMESTAMP;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;;

-- 4. trg_set_fecha_pago_deudores
DROP TRIGGER IF EXISTS trg_set_fecha_pago_deudores ON deudores;;
CREATE TRIGGER trg_set_fecha_pago_deudores
    BEFORE UPDATE OF estado ON deudores
    FOR EACH ROW
    EXECUTE FUNCTION fn_set_fecha_pago_deudores();;

-- Function to prevent audit mutations
CREATE OR REPLACE FUNCTION fn_audit_no_mutation()
    RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'La tabla auditoria es inmutable: no se permiten modificaciones.';
    ELSIF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'La tabla auditoria es inmutable: no se permiten eliminaciones.';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;;

-- 5. Inmutabilidad de la tabla auditoria
DROP TRIGGER IF EXISTS trg_audit_no_update ON auditoria;;
CREATE TRIGGER trg_audit_no_update
    BEFORE UPDATE ON auditoria
    FOR EACH ROW
    EXECUTE FUNCTION fn_audit_no_mutation();;

DROP TRIGGER IF EXISTS trg_audit_no_delete ON auditoria;;
CREATE TRIGGER trg_audit_no_delete
    BEFORE DELETE ON auditoria
    FOR EACH ROW
    EXECUTE FUNCTION fn_audit_no_mutation();;