-- V2__Release_2_0.sql
-- Combined migration for CentralizeSys Version 2.0 Feature Set.

-- =======================================================================================
-- PART 1: VENTAS SINGLE TABLE REFACTOR & SCHEMA AUGMENTATION
-- =======================================================================================

-- 1. Ampliar CHECK constraint de ventas.estado
DO $$
    DECLARE
        rec RECORD;
    BEGIN
        FOR rec IN
            SELECT oid, conname
            FROM pg_constraint
            WHERE conrelid = 'ventas'::regclass AND contype = 'c'
            LOOP
                IF pg_get_constraintdef(rec.oid) ILIKE '%estado%' THEN
                    EXECUTE 'ALTER TABLE ventas DROP CONSTRAINT ' || rec.conname;
                END IF;
            END LOOP;
    END $$;
ALTER TABLE ventas ADD CONSTRAINT ventas_estado_check CHECK (estado IN ('ACTIVA', 'ANULADA', 'PENDIENTE', 'CANCELADA_PENDIENTE'));

-- 2. Agregar fecha_creacion a ventas y backfill
ALTER TABLE ventas ADD COLUMN IF NOT EXISTS fecha_creacion TIMESTAMP;
UPDATE ventas SET fecha_creacion = fecha WHERE fecha_creacion IS NULL;

-- 3. Agregar anulado a detalles_venta
ALTER TABLE detalles_venta ADD COLUMN IF NOT EXISTS anulado BOOLEAN NOT NULL DEFAULT FALSE;

-- 4. Agregar anulado, usuario_id y fecha_pago a pagos_venta
ALTER TABLE pagos_venta ADD COLUMN IF NOT EXISTS anulado BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE pagos_venta ADD COLUMN IF NOT EXISTS usuario_id INTEGER;
ALTER TABLE pagos_venta ADD COLUMN IF NOT EXISTS fecha_pago TIMESTAMP;

UPDATE pagos_venta
SET fecha_pago = (SELECT fecha FROM ventas WHERE ventas.id = pagos_venta.venta_id)
WHERE fecha_pago IS NULL;

DO $$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_pagos_venta_usuario') THEN
            ALTER TABLE pagos_venta ADD CONSTRAINT fk_pagos_venta_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id);
        END IF;
    END $$;

-- 5. Migrar datos de ventas_pendientes a ventas
DO $$
    DECLARE
        v_rec RECORD;
        v_nueva_venta_id INTEGER;
        d_rec RECORD;
        p_rec RECORD;
    BEGIN
        FOR v_rec IN SELECT * FROM ventas_pendientes WHERE estado IN ('PENDIENTE', 'CANCELADA') LOOP
                INSERT INTO ventas (
                    fecha, fecha_creacion, cliente_nombre, total_venta, descuento_global,
                    tipo_venta, estado, usuario_id
                ) VALUES (
                             v_rec.fecha, v_rec.fecha, v_rec.cliente_nombre, v_rec.total_estimado, v_rec.descuento_global,
                             v_rec.tipo_venta,
                             CASE
                                 WHEN v_rec.estado = 'PENDIENTE' THEN 'PENDIENTE'
                                 WHEN v_rec.estado = 'CANCELADA' THEN 'CANCELADA_PENDIENTE'
                                 END,
                             v_rec.usuario_id
                         ) RETURNING id INTO v_nueva_venta_id;

                FOR d_rec IN SELECT * FROM detalles_venta_pendiente WHERE venta_pendiente_id = v_rec.id LOOP
                        INSERT INTO detalles_venta (
                            venta_id, producto_id, descripcion_snapshot, codigo_snapshot, costo_snapshot,
                            cantidad, precio_lista, descuento_valor, precio_unitario, subtotal, anulado
                        ) VALUES (
                                     v_nueva_venta_id, d_rec.producto_id, d_rec.descripcion_snapshot, d_rec.codigo_snapshot, d_rec.costo_snapshot,
                                     d_rec.cantidad, d_rec.precio_lista, d_rec.descuento_valor, d_rec.precio_unitario, d_rec.subtotal, d_rec.anulado
                                 );
                    END LOOP;

                FOR p_rec IN SELECT * FROM pagos_venta_pendiente WHERE venta_pendiente_id = v_rec.id LOOP
                        INSERT INTO pagos_venta (
                            venta_id, metodo_pago_id, monto, fecha_pago, anulado, usuario_id
                        ) VALUES (
                                     v_nueva_venta_id, p_rec.metodo_pago_id, p_rec.monto, p_rec.fecha_pago, p_rec.anulado, p_rec.usuario_id
                                 );
                    END LOOP;
            END LOOP;
    END $$;

-- 6. Eliminar tablas legacy
DROP TABLE IF EXISTS pagos_venta_pendiente;
DROP TABLE IF EXISTS detalles_venta_pendiente;
DROP TABLE IF EXISTS ventas_pendientes;

-- =======================================================================================
-- PART 2: SISTEMA DE CHEQUES Y ALERTAS
-- =======================================================================================

-- 1. Agregar columna activo a metodos_pago e inhabilitar TBF
ALTER TABLE metodos_pago ADD COLUMN IF NOT EXISTS activo BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE metodos_pago SET activo = FALSE WHERE acronimo IN ('TBF', 'TBFBBVA');

-- 2. Crear tabla alertas_cheques con su Foreign Key a pagos_venta (cancelaciones)
CREATE TABLE IF NOT EXISTS alertas_cheques (
                                               id SERIAL PRIMARY KEY,
                                               venta_id INTEGER NOT NULL,
                                               monto REAL NOT NULL,
                                               fecha_cobro DATE NOT NULL,
                                               estado TEXT DEFAULT 'PENDIENTE' CHECK(estado IN ('PENDIENTE', 'COBRADO', 'ANULADA')),
                                               pago_venta_id INTEGER,
                                               FOREIGN KEY (venta_id) REFERENCES ventas(id),
                                               CONSTRAINT fk_alertas_cheques_pagos_venta FOREIGN KEY (pago_venta_id) REFERENCES pagos_venta(id) ON DELETE SET NULL
);

-- =======================================================================================
-- PART 3: GASTOS CAJA
-- =======================================================================================
CREATE TABLE IF NOT EXISTS gastos_caja (
                                           id SERIAL PRIMARY KEY,
                                           monto REAL NOT NULL,
                                           motivo TEXT NOT NULL,
                                           fecha_gasto TIMESTAMP NOT NULL,
                                           fecha_registro TIMESTAMP NOT NULL,
                                           persona_involucrada TEXT,
                                           registrado_por_usuario_id INTEGER,
                                           categoria TEXT,
                                           anulado BOOLEAN NOT NULL DEFAULT FALSE,
                                           razon_anulacion TEXT,
                                           FOREIGN KEY (registrado_por_usuario_id) REFERENCES usuarios(id)
);
