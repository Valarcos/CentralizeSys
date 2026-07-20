-- V4__Fix_Pagos_Venta_Fecha_Pago.sql
-- Adds the missing 'fecha_pago' column to the 'pagos_venta' table.
-- This column is required for Cash Flow (Flujo de Caja) calculations in the Reports screen.

ALTER TABLE pagos_venta ADD COLUMN IF NOT EXISTS fecha_pago TIMESTAMP;

-- Backfill missing dates with the original sale date
UPDATE pagos_venta
SET fecha_pago = (SELECT fecha FROM ventas WHERE ventas.id = pagos_venta.venta_id)
WHERE fecha_pago IS NULL;
