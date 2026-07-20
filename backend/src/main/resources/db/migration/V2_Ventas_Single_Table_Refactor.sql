-- V2__Ventas_Single_Table_Refactor.sql

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

-- 2. Agregar fecha_creacion a ventas
ALTER TABLE ventas ADD COLUMN IF NOT EXISTS fecha_creacion TIMESTAMP;

-- 3. Backfill fecha_creacion (para históricas, usamos la fecha de la venta original)
UPDATE ventas SET fecha_creacion = fecha WHERE fecha_creacion IS NULL;

-- 4. Agregar anulado a detalles_venta
ALTER TABLE detalles_venta ADD COLUMN IF NOT EXISTS anulado BOOLEAN NOT NULL DEFAULT FALSE;

-- 5. Agregar anulado y usuario_id a pagos_venta
ALTER TABLE pagos_venta ADD COLUMN IF NOT EXISTS anulado BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE pagos_venta ADD COLUMN IF NOT EXISTS usuario_id INTEGER;

DO $$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_pagos_venta_usuario') THEN
            ALTER TABLE pagos_venta ADD CONSTRAINT fk_pagos_venta_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id);
        END IF;
    END $$;

-- 6. Migrar datos de ventas_pendientes a ventas
-- IMPORTANTE: Solo se migran las PENDIENTE y CANCELADA.
-- Las FINALIZADA ya se copiaron a la tabla 'ventas' cuando se finalizaron originalment.
DO $$
    DECLARE
        v_rec RECORD;
        v_nueva_venta_id INTEGER;
        d_rec RECORD;
        p_rec RECORD;
    BEGIN
        FOR v_rec IN SELECT * FROM ventas_pendientes WHERE estado IN ('PENDIENTE', 'CANCELADA') LOOP

                -- Insertar en ventas y recuperar el nuevo ID
                INSERT INTO ventas (
                    fecha,
                    fecha_creacion,
                    cliente_nombre,
                    total_venta,
                    descuento_global,
                    tipo_venta,
                    estado,
                    usuario_id
                ) VALUES (
                             v_rec.fecha,
                             v_rec.fecha, -- Al ser pendiente, fecha (reserva) y fecha_creacion son la misma por ahora
                             v_rec.cliente_nombre,
                             v_rec.total_estimado,
                             v_rec.descuento_global,
                             v_rec.tipo_venta,
                             CASE
                                 WHEN v_rec.estado = 'PENDIENTE' THEN 'PENDIENTE'
                                 WHEN v_rec.estado = 'CANCELADA' THEN 'CANCELADA_PENDIENTE'
                                 END,
                             v_rec.usuario_id
                         ) RETURNING id INTO v_nueva_venta_id;

                -- Migrar detalles
                FOR d_rec IN SELECT * FROM detalles_venta_pendiente WHERE venta_pendiente_id = v_rec.id LOOP
                        INSERT INTO detalles_venta (
                            venta_id,
                            producto_id,
                            descripcion_snapshot,
                            codigo_snapshot,
                            costo_snapshot,
                            cantidad,
                            precio_lista,
                            descuento_valor,
                            precio_unitario,
                            subtotal,
                            anulado
                        ) VALUES (
                                     v_nueva_venta_id,
                                     d_rec.producto_id,
                                     d_rec.descripcion_snapshot,
                                     d_rec.codigo_snapshot,
                                     d_rec.costo_snapshot,
                                     d_rec.cantidad,
                                     d_rec.precio_lista,
                                     d_rec.descuento_valor,
                                     d_rec.precio_unitario,
                                     d_rec.subtotal,
                                     d_rec.anulado
                                 );
                    END LOOP;

                -- Migrar pagos
                FOR p_rec IN SELECT * FROM pagos_venta_pendiente WHERE venta_pendiente_id = v_rec.id LOOP
                        INSERT INTO pagos_venta (
                            venta_id,
                            metodo_pago_id,
                            monto,
                            fecha_pago,
                            anulado,
                            usuario_id
                        ) VALUES (
                                     v_nueva_venta_id,
                                     p_rec.metodo_pago_id,
                                     p_rec.monto,
                                     p_rec.fecha_pago,
                                     p_rec.anulado,
                                     p_rec.usuario_id
                                 );
                    END LOOP;

            END LOOP;
    END $$;

-- 7. Eliminar tablas legacy
DROP TABLE IF EXISTS pagos_venta_pendiente;
DROP TABLE IF EXISTS detalles_venta_pendiente;
DROP TABLE IF EXISTS ventas_pendientes;
